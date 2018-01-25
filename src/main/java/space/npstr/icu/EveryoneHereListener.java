/*
 * Copyright (C) 2018 Dennis Neufeld
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

package space.npstr.icu;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

/**
 * Created by napster on 25.01.18.
 * <p>
 * Listens for messages containing everyone / here mentions and give the user using it that role to troll them back
 */
public class EveryoneHereListener extends ListenerAdapter {

    private final DatabaseWrapper dbWrapper;

    public EveryoneHereListener(DatabaseWrapper wrapper) {
        this.dbWrapper = wrapper;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }

        GuildSettings guildSettings = dbWrapper.getOrCreate(GuildSettings.key(guild));

        //dont troll admins
        if (CommandsListener.isAdmin(dbWrapper, event.getMember())) {
            return;
        }

        Message msg = event.getMessage();

        Long hereId = guildSettings.getHereRoleId();
        Role hereRole = hereId != null ? guild.getRoleById(hereId) : null;
        if (hereRole != null
                && (msg.isMentioned(null, Message.MentionType.HERE) || msg.isMentioned(hereRole))
                && !msg.getMember().getRoles().contains(hereRole)) {
            addRole(msg, hereRole);
        }

        Long everyoneId = guildSettings.getEveryoneRoleId();
        Role everyoneRole = everyoneId != null ? guild.getRoleById(everyoneId) : null;
        if (everyoneRole != null
                && (msg.isMentioned(null, Message.MentionType.EVERYONE) || msg.isMentioned(everyoneRole))
                && !msg.getMember().getRoles().contains(everyoneRole)) {
            addRole(msg, everyoneRole);
        }
    }

    //msg needs to be from a guild
    private void addRole(Message msg, Role role) {
        if (role.getGuild().getSelfMember().canInteract(role)) {
            role.getGuild().getController().addSingleRoleToMember(msg.getMember(), role).queue(__ -> {
                if (role.getGuild().getSelfMember().hasPermission(msg.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
                    msg.addReaction("👌").queue();
                    msg.addReaction("👌🏻").queue();
                    msg.addReaction("👌🏼").queue();
                    msg.addReaction("👌🏽").queue();
                    msg.addReaction("👌🏾").queue();
                    msg.addReaction("👌🏿").queue();
                }
            });
        }

    }
}
