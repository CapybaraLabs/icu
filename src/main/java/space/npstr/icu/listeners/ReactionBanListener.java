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

package space.npstr.icu.listeners;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import space.npstr.icu.db.entities.GuildSettingsRepository;
import space.npstr.icu.db.entities.ReactionBan;
import space.npstr.icu.db.entities.ReactionBanRepository;
import space.npstr.icu.db.entities.ReportingChannelFetcher;

/**
 * Created by napster on 01.05.19.
 */
@Component
public class ReactionBanListener extends ListenerAdapter {

    private final GuildSettingsRepository guildSettingsRepo;
    private final ReactionBanRepository reactionBanRepo;
    private final ReportingChannelFetcher reportingChannelFetcher;


    public ReactionBanListener(GuildSettingsRepository guildSettingsRepo, ReactionBanRepository reactionBanRepo, ReportingChannelFetcher reportingChannelFetcher) {
        this.guildSettingsRepo = guildSettingsRepo;
        this.reactionBanRepo = reactionBanRepo;
        this.reportingChannelFetcher = reportingChannelFetcher;
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        this.reactionBanRepo.findById(ReactionBan.key(event.getChannel(), event.getReaction().getEmoji()))
            .ifPresent(ban -> {
                User user = event.getUser();
                if (user == null) {
                    user = event.retrieveUser().submit().join();
                }
                deleteReaction(event, user);
                issueBan(event.getGuild(), user, ban);
            });
    }

    private void deleteReaction(MessageReactionAddEvent event, User user) {
        if (!event.isFromGuild()) {
            return;
        }

        Guild guild = event.getGuild();
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            //try to report the issue
            Long reportingChannelId = guildSettingsRepo.findOrCreateByGuild(guild).getReportingChannelId();
            if (reportingChannelId != null) {
                TextChannel reportingChannel = guild.getTextChannelById(reportingChannelId);
                if (reportingChannel != null) {
                    reportingChannel.sendMessage("I am missing the message manage permission in "
                            + channel.getAsMention() + " to delete a banned reaction."
                            + " Please fix the permission or remove reaction bans for this guild.").queue();
                }
            }
            return;
        }
        event.getReaction().removeReaction(user).queue();
    }

    private void issueBan(Guild guild, User user, ReactionBan reactionBan) {
        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            //try to report the issue
            Long reportingChannelId = guildSettingsRepo.findOrCreateByGuild(guild).getReportingChannelId();
            if (reportingChannelId != null) {
                TextChannel reportingChannel = guild.getTextChannelById(reportingChannelId);
                if (reportingChannel != null) {
                    reportingChannel.sendMessage("I am missing the ban permission to perform a reaction ban."
                            + " Please fix the permission or remove reaction bans for this guild.").queue();
                }
            }
            return;
        }

        String channelName = Optional.ofNullable(guild.getTextChannelById(reactionBan.getId().getChannelId()))
                .map(tc -> "#" + tc.getName())
                .orElseGet(() -> Long.toString(reactionBan.getId().getChannelId()));

        String reason = String.format("[i.c.u. Reaction Ban] User reacted with %s in channel %s",
                reactionBan.getId().getEmote(), channelName);
        if (reason.length() >= 512) { //max audit log size
            reason = reason.substring(0, 512);
        }

        String message = String.format("You are banned from **%s** for reacting with %s in channel %s. Better luck next time.",
                guild.getName(), reactionBan.getId().getEmote(), channelName);

        final String theReason = reason;
        user.openPrivateChannel().submit()
            .thenCompose(pc -> pc.sendMessage(message).submit())
            .whenComplete((__, ___) -> guild.ban(user, 0, TimeUnit.DAYS).reason(theReason).queue());


        Optional<TextChannel> textChannel = reportingChannelFetcher.fetchWorkingReportingChannel(guild);
        if (textChannel.isEmpty()) {
            return;
        }
        TextChannel reportingChannel = textChannel.get();
        String report = String.format("User %s (%s): %nBanned for reacting with %s in channel <#%s>",
                user.getAsMention(), user, reactionBan.getId().getEmote(), reactionBan.getId().getChannelId());

        reportingChannel.sendMessage(report).queue();
    }
}
