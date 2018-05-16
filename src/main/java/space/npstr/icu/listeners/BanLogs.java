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

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.AuditLogUtil;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.CheckReturnValue;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Created by napster on 16.05.18.
 */
public class BanLogs extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(BanLogs.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;

    public BanLogs(Supplier<DatabaseWrapper> wrapperSupplier) {
        this.wrapperSupp = wrapperSupplier;
    }


    @Override public void onGuildBan(GuildBanEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        getExecutor(event.getGuild()).execute(() -> onBan(event, now));
    }

    @Override public void onGuildUnban(GuildUnbanEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        getExecutor(event.getGuild()).execute(() -> onUnban(event, now));
    }

    @Override public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        getExecutor(event.getGuild()).execute(() -> onLeave(event, now));
    }

    private static final String BAN_FORMAT = "User <@%s> (%s) banned:\n%s";

    private void onBan(GuildBanEvent event, OffsetDateTime eventTime) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        fetchWorkingLogChannel(guild).ifPresent(
                logChannel -> {
                    String reason = AuditLogUtil.getBanReason(guild, user, eventTime)
                            .orElse("Reason could not be retrieved");
                    logChannel.sendMessage(String.format(BAN_FORMAT, user.getIdLong(), user, reason)).queue();
                }
        );
    }

    private static final String UNBAN_FORMAT = "User <@%s> (%s) unbanned by %s";

    private void onUnban(GuildUnbanEvent event, OffsetDateTime eventTime) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        fetchWorkingLogChannel(guild).ifPresent(
                logChannel -> {
                    String unbannerName = AuditLogUtil.getUnbanner(guild, user, eventTime)
                            .map(unbanner -> String.format("<@%s> (%s)", unbanner.getIdLong(), unbanner))
                            .orElse("Unbanner could not be retrieved");
                    logChannel.sendMessage(String.format(UNBAN_FORMAT, user.getIdLong(), user, unbannerName)).queue();
                }
        );
    }

    private static final String KICK_FORMAT = "User <@%s> (%s) kicked:\n%s";

    private void onLeave(GuildMemberLeaveEvent event, OffsetDateTime eventTime) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        fetchWorkingLogChannel(guild).ifPresent(
                logChannel -> {
                    String reason = AuditLogUtil.getKickEntry(guild, user, eventTime)
                            .map(entry -> String.format("By <@%s> (%s) for: %s", entry.getUser().getIdLong(), entry.getUser(), entry.getReason()))
                            .orElse("Failed to fetch audit log entry.");
                    logChannel.sendMessage(String.format(KICK_FORMAT, user.getIdLong(), user, reason)).queue();
                }
        );
    }


    //returns the log channel where we can post in of the provided guild
    // and takes appropriate measures if it failed to do so
    @CheckReturnValue
    private Optional<TextChannel> fetchWorkingLogChannel(Guild guild) {
        Long logChannelId = wrapperSupp.get().getOrCreate(GuildSettings.key(guild)).getLogChannelId();
        if (logChannelId == null) {
            return Optional.empty();
        }
        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null || !logChannel.canTalk()) {
            for (TextChannel textChannel : guild.getTextChannels()) {
                if (textChannel.canTalk()) {
                    textChannel.sendMessage("A log channel <#" + logChannelId + "> was configured, "
                            + "but it appears to be deleted or I can't write there. Please tell an admin of this guild "
                            + "to either fix permissions, set a new channel, or reset my reporting channel configuration.").queue();
                    return Optional.empty();
                }
            }
            // meh...cant report the issue, raising an warn/error level log is kinda ok since this is selfhosted
            log.warn("Guild {} has a broken log channel", guild);
            return Optional.empty();
        }

        return Optional.of(logChannel);
    }
}
