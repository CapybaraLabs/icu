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

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.requests.RequestFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.AuditLogUtil;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.CheckReturnValue;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Created by napster on 10.03.18.
 * <p>
 * Warns guilds about users being banned in other guilds, or users with a fresh account joining.
 */
public class SuspiciousUsersWarner extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousUsersWarner.class);

    private static final int MIN_ACCOUNT_AGE_MINUTES = 30;

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final Supplier<ShardManager> shardManagerSupp;

    public SuspiciousUsersWarner(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.wrapperSupp = wrapperSupplier;
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

            Optional<TextChannel> textChannel = fetchWorkingReportingChannel(guild);
            if (!textChannel.isPresent()) {
                return;
            }
            TextChannel reportingChannel = textChannel.get();
            String messsage = "User " + bannedUser.getAsMention() + " (" + bannedUser + "):\n";
            messsage += "Banned in " + bannedGuild.getName() + " with reason: " + reason;

            reportingChannel.sendMessage(messsage).queue();
        });
    }

    private void memberJoined(GuildMemberJoinEvent event) {
        Optional<TextChannel> textChannel = fetchWorkingReportingChannel(event.getGuild());
        if (!textChannel.isPresent()) {
            return;
        }
        TextChannel reportingChannel = textChannel.get();

        Map<Guild, RequestFuture<List<Guild.Ban>>> banLists = new HashMap<>();
        shardManagerSupp.get().getGuildCache().forEach(guild -> {
            if (guild.isAvailable() && guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                banLists.put(guild, guild.getBanList().submit());
            }
        });

        Map<Guild, Guild.Ban> userBans = new HashMap<>();
        for (Map.Entry<Guild, RequestFuture<List<Guild.Ban>>> entry : banLists.entrySet()) {
            List<Guild.Ban> bans = new ArrayList<>();
            try {
                bans.addAll(entry.getValue().get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Failed to fetch ban list for guild {}", entry.getKey(), e);
            }
            Optional<Guild.Ban> ban = bans.stream()
                    .filter(b -> b.getUser().equals(event.getUser()))
                    .findAny();
            ban.ifPresent(b -> userBans.put(entry.getKey(), b));
        }

        StringBuilder out = new StringBuilder();
        if (event.getUser().getCreationTime().isAfter(OffsetDateTime.now().minusMinutes(MIN_ACCOUNT_AGE_MINUTES))) {//report accounts younger than 30 minutes
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
            String user = "User " + event.getUser().getAsMention() + " (" + event.getUser() + "):\n";
            reportingChannel.sendMessage(user + out.toString()).queue();
        }
    }

    //returns the reporting channe lwhere we can post in of the guild
    // and takes appropriate measures if it failed to do so
    @CheckReturnValue
    private Optional<TextChannel> fetchWorkingReportingChannel(Guild guild) {
        Long reportingChannelId = wrapperSupp.get().getOrCreate(GuildSettings.key(guild)).getReportingChannelId();
        if (reportingChannelId == null) {
            return Optional.empty();
        }
        TextChannel reportingChannel = guild.getTextChannelById(reportingChannelId);
        if (reportingChannel == null || !reportingChannel.canTalk()) {
            for (TextChannel textChannel : guild.getTextChannels()) {
                if (textChannel.canTalk()) {
                    textChannel.sendMessage("A reporting channel <#" + reportingChannelId + "> was configured, "
                            + "but it appears to be deleted or I can't write there. Please tell an admin of this guild "
                            + "to either fix permissions, set a new channel, or reset my reporting channel configuration.").queue();
                    return Optional.empty();
                }
            }
            // meh...cant report the issue, raising an warn/error level log is kinda ok since this is selfhosted
            log.warn("Guild {} has a broken reporting channel", guild);
            return Optional.empty();
        }

        return Optional.of(reportingChannel);
    }
}
