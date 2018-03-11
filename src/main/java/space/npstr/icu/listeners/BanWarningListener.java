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
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.requests.RequestFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

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
 * Warns guilds about users being banned in other guilds.
 */
public class BanWarningListener extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(BanWarningListener.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final Supplier<ShardManager> shardManagerSupp;

    public BanWarningListener(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.wrapperSupp = wrapperSupplier;
        this.shardManagerSupp = shardManagerSupplier;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> memberJoined(event));
    }

    private void memberJoined(GuildMemberJoinEvent event) {
        Long reportingChannelId = wrapperSupp.get().getOrCreate(GuildSettings.key(event.getGuild())).getReportingChannelId();
        if (reportingChannelId == null) {
            return;
        }
        TextChannel reportingChannel = event.getGuild().getTextChannelById(reportingChannelId);
        if (reportingChannel == null || !reportingChannel.canTalk()) {
            for (TextChannel textChannel : event.getGuild().getTextChannels()) {
                if (textChannel.canTalk()) {
                    textChannel.sendMessage("A reporting channel <#" + reportingChannelId + "> was configured, "
                            + "but it appears to be deleted or I can't write there. Please tell an admin of this guild "
                            + "to either fix permissions, set a new channel, or reset my reporting channel configuration.").queue();
                    return;
                }
            }
            // meh...cant report the issue, raising an warn/error level log is kinda ok since this is selfhosted
            log.warn("Guild {} has a broken reporting channel", event.getGuild());
            return;
        }

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

        if (!userBans.isEmpty()) {
            StringBuilder out = new StringBuilder("User ").append(event.getUser().getAsMention()).append(" (")
                    .append(event.getUser().getId()).append(") is banned in ").append(userBans.size()).append(" guilds:\n");
            for (Map.Entry<Guild, Guild.Ban> ban : userBans.entrySet()) {
                out.append(ban.getKey().getName()).append(": ").append(ban.getValue().getReason()).append("\n");
            }
            reportingChannel.sendMessage(out.toString()).queue();
        }
    }
}
