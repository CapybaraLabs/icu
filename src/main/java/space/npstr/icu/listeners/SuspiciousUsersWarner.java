/*
 * Copyright (C) 2017 - 2018 Dennis Neufeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package space.npstr.icu.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.AuditLogUtil;
import space.npstr.icu.Main;
import space.npstr.icu.db.entities.ReportingChannelFetcher;
import space.npstr.sqlsauce.DatabaseWrapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Created by napster on 10.03.18.
 * <p>
 * Warns guilds about users being banned in other guilds, or users with a fresh account joining.
 */
public class SuspiciousUsersWarner extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousUsersWarner.class);

    private static final int MIN_ACCOUNT_AGE_MINUTES = 30;

    private final ReportingChannelFetcher reportingChannelFetcher;
    private final Supplier<ShardManager> shardManagerSupp;

    public SuspiciousUsersWarner(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.reportingChannelFetcher = new ReportingChannelFetcher(wrapperSupplier);
        this.shardManagerSupp = shardManagerSupplier;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> memberJoined(event));
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        getExecutor(event.getGuild()).execute(() -> memberBanned(event, now));
    }

    private void memberBanned(GuildBanEvent event, OffsetDateTime eventTime) {
        Guild bannedGuild = event.getGuild();
        User bannedUser = event.getUser();
        final String reason = AuditLogUtil.getBanReason(bannedGuild, bannedUser, eventTime)
                .orElse("Reason could not be retrieved");
        //check whether the banned user is part of other guilds, and notify them
        bannedUser.getMutualGuilds().forEach(guild -> {
            //dont notify guild where this user was just banned
            if (guild.getIdLong() == bannedGuild.getIdLong()) {
                return;
            }

            Optional<TextChannel> textChannel = reportingChannelFetcher.fetchWorkingReportingChannel(guild);
            if (textChannel.isEmpty()) {
                return;
            }
            TextChannel reportingChannel = textChannel.get();
            String messsage = "Member " + bannedUser.getAsMention() + " (" + bannedUser + "):\n";
            messsage += "Banned just now in " + bannedGuild.getName() + " with reason: " + reason;

            reportingChannel.sendMessage(messsage).queue();
        });
    }

    private void memberJoined(GuildMemberJoinEvent event) {
        Optional<TextChannel> textChannel = reportingChannelFetcher.fetchWorkingReportingChannel(event.getGuild());
        if (textChannel.isEmpty()) {
            return;
        }
        TextChannel reportingChannel = textChannel.get();

        Map<Guild, CompletableFuture<Guild.Ban>> banLists = new HashMap<>();
        shardManagerSupp.get().getGuildCache().forEach(guild -> {
            if (guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                banLists.put(guild, guild.retrieveBan(event.getUser()).submit());
            }
        });

        Map<Guild, Guild.Ban> userBans = new HashMap<>();
        for (Map.Entry<Guild, CompletableFuture<Guild.Ban>> entry : banLists.entrySet()) {
            List<Guild.Ban> bans = new ArrayList<>();
            try {
                bans.add(entry.getValue().join());
            } catch (Exception e) {
                Throwable realCause = Main.unwrap(e);
                if (!(realCause instanceof ErrorResponseException)
                        || ((ErrorResponseException) realCause).getErrorResponse() != ErrorResponse.UNKNOWN_BAN) {
                    log.error("Failed to fetch ban list for guild {}", entry.getKey(), realCause);
                }
            }
            Optional<Guild.Ban> ban = bans.stream()
                    .filter(b -> b.getUser().equals(event.getUser()))
                    .findAny();
            ban.ifPresent(b -> userBans.put(entry.getKey(), b));
        }

        StringBuilder out = new StringBuilder();
        if (event.getUser().getTimeCreated().isAfter(OffsetDateTime.now().minusMinutes(MIN_ACCOUNT_AGE_MINUTES))) {//report accounts younger than 30 minutes
            out.append("Account younger than ").append(MIN_ACCOUNT_AGE_MINUTES).append(" minutes").append("\n");
        }
        if (!userBans.isEmpty()) {
            out.append("Banned in ").append(userBans.size()).append(" guilds:\n```\n");
            for (Map.Entry<Guild, Guild.Ban> ban : userBans.entrySet()) {
                out.append(ban.getKey().getName()).append(" with reason: ").append(ban.getValue().getReason()).append("\n");
            }
            out.append("\n```");
        }

        if (out.length() > 0) {
            String user = "User " + event.getUser().getAsMention() + " (" + event.getUser() + ") joined this server:\n";
            reportingChannel.sendMessage(user + out).queue();
        }
    }
}
