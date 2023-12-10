/*
 * Copyright (C) 2017 - 2023 Dennis Neufeld
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

package space.npstr.icu.db.entities;

import java.util.Optional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

/**
 * Created by napster on 01.05.19.
 * <p>
 * Return the reporting channel of individual guilds where we can post our reports in
 * and take appropriate measures if we fail to look up such a channel
 */
@Component
public class ReportingChannelFetcher {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportingChannelFetcher.class);

    private final GuildSettingsRepository guildSettingsRepo;

    public ReportingChannelFetcher(GuildSettingsRepository guildSettingsRepo) {
        this.guildSettingsRepo = guildSettingsRepo;
    }

    public Optional<TextChannel> fetchWorkingReportingChannel(Guild guild) {
        Long reportingChannelId = guildSettingsRepo.findOrCreateByGuild(guild).getReportingChannelId();
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
