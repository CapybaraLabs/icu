/*
 * Copyright (C) 2017 - 2019 Dennis Neufeld
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

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.icu.db.entities.ReactionBan;
import space.npstr.icu.db.entities.ReportingChannelFetcher;
import space.npstr.sqlsauce.DatabaseWrapper;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Created by napster on 01.05.19.
 */
public class ReactionBanListener extends ListenerAdapter {

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final ReportingChannelFetcher reportingChannelFetcher;


    public ReactionBanListener(Supplier<DatabaseWrapper> wrapperSupp) {
        this.wrapperSupp = wrapperSupp;
        this.reportingChannelFetcher = new ReportingChannelFetcher(wrapperSupp);
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        Optional.ofNullable(wrapperSupp.get().getEntity(ReactionBan.key(event.getChannel(), event.getReactionEmote())))
                .ifPresent(ban -> {
                    deleteReaction(event);
                    issueBan(event.getGuild(), event.getUser(), ban);
                });
    }

    private void deleteReaction(GuildMessageReactionAddEvent event) {
        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            //try to report the issue
            GuildSettings settings = wrapperSupp.get().getOrCreate(GuildSettings.key(guild));
            Long reportingChannelId = settings.getReportingChannelId();
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

        event.getReaction().removeReaction(event.getUser()).queue();
    }

    private void issueBan(Guild guild, User user, ReactionBan reactionBan) {
        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            //try to report the issue
            GuildSettings settings = wrapperSupp.get().getOrCreate(GuildSettings.key(guild));
            Long reportingChannelId = settings.getReportingChannelId();
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
                .whenComplete((__, ___) -> guild.getController().ban(user, 0, theReason).queue());


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
