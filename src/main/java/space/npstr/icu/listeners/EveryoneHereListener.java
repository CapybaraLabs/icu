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

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

/**
 * Created by napster on 25.01.18.
 * <p>
 * Listens for messages containing everyone / here mentions and give the user using it that role to troll them back
 */
@Component
public class EveryoneHereListener extends ThreadedListener {

    private final DatabaseWrapper wrapper;

    public EveryoneHereListener(DatabaseWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            getExecutor(event.getGuild()).execute(() -> guildMessageReceived(event));
        }
    }

    private void guildMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            return;
        }
        Guild guild = event.getGuild();
        GuildSettings guildSettings = wrapper.getOrCreate(GuildSettings.key(guild));

        //dont troll admins
        if (CommandsListener.isAdmin(wrapper, member)) {
            return;
        }

        Message msg = event.getMessage();

        Long hereId = guildSettings.getHereRoleId();
        Role hereRole = hereId != null ? guild.getRoleById(hereId) : null;
        if (hereRole != null
            && (msg.getMentions().mentionsEveryone() || msg.getMentions().isMentioned(hereRole))
            && !member.getRoles().contains(hereRole)) {
            addRole(msg, member, hereRole);
        }

        Long everyoneId = guildSettings.getEveryoneRoleId();
        Role everyoneRole = everyoneId != null ? guild.getRoleById(everyoneId) : null;
        if (everyoneRole != null
            && (msg.getMentions().mentionsEveryone() || msg.getMentions().isMentioned(everyoneRole))
            && !member.getRoles().contains(everyoneRole)) {
            addRole(msg, member, everyoneRole);
        }
    }

    //msg needs to be from a guild
    private void addRole(Message msg, Member member, Role role) {
        if (role.getGuild().getSelfMember().canInteract(role)) {
            role.getGuild().addRoleToMember(member, role).queue(__ -> {
                if (role.getGuild().getSelfMember().hasPermission(msg.getChannel().asGuildMessageChannel(), Permission.MESSAGE_ADD_REACTION)) {
                    msg.addReaction(Emoji.fromUnicode("👌")).queue();
                    msg.addReaction(Emoji.fromUnicode("👌🏻")).queue();
                    msg.addReaction(Emoji.fromUnicode("👌🏼")).queue();
                    msg.addReaction(Emoji.fromUnicode("👌🏽")).queue();
                    msg.addReaction(Emoji.fromUnicode("👌🏾")).queue();
                    msg.addReaction(Emoji.fromUnicode("👌🏿")).queue();
                }
            });
        }

    }
}
