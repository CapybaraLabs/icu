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

import net.dv8tion.jda.bot.entities.ApplicationInfo;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.IMentionable;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.Main;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by napster on 25.01.18.
 * <p>
 * yeah this is ugly af without any command / context framework
 */
public class CommandsListener extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(CommandsListener.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final Supplier<ShardManager> shardManagerSupp;


    public CommandsListener(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.wrapperSupp = wrapperSupplier;
        this.shardManagerSupp = shardManagerSupplier;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        getExecutor(event.getGuild()).execute(() -> guildMessageReceived(event));
    }

    private void guildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (!event.getChannel().canTalk()) {
            return;
        }

        if (!event.getMessage().getMentionedUsers().contains(event.getJDA().getSelfUser())) {
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }

        if (!isAdmin(wrapperSupp.get(), event.getMember())) {
            return;
        }

        Message msg = event.getMessage();
        String content = msg.getContentRaw();

        log.info("Mention received: " + msg.getContentDisplay());

        if (content.contains("set everyone")) {
            if (msg.getMentionedRoles().isEmpty()) {
                event.getChannel().sendMessage("Please mention the role you want to set").queue();
                return;
            }
            Role r = msg.getMentionedRoles().get(0);

            if (!guild.getSelfMember().canInteract(r)) {
                event.getChannel().sendMessage("I cannot interact with that role. Please choose another one or move my bot role higher.").queue();
                return;
            }

            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> gs.setEveryoneRole(r));
            event.getChannel().sendMessage("Set up " + r.getAsMention() + " as everyone role " + "👌👌🏻👌🏼👌🏽👌🏾👌🏿").queue();
        } else if (content.contains("reset everyone")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), GuildSettings::resetEveryoneRole);
            event.getChannel().sendMessage("Reset the everyone role").queue();
        } else if (content.contains("set here")) {
            if (msg.getMentionedRoles().isEmpty()) {
                event.getChannel().sendMessage("Please mention the role you want to set").queue();
                return;
            }
            Role r = msg.getMentionedRoles().get(0);

            if (!guild.getSelfMember().canInteract(r)) {
                event.getChannel().sendMessage("I cannot interact with that role. Please choose another one or move my bot role higher.").queue();
                return;
            }

            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> gs.setHereRole(r));
            event.getChannel().sendMessage("Set up " + r.getAsMention() + " as here role " + "👌👌🏻👌🏼👌🏽👌🏾👌🏿").queue();
        } else if (content.contains("reset here")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), GuildSettings::resetHereRole);
            event.getChannel().sendMessage("Reset the here role").queue();
        } else if (content.contains("add admin")) {
            List<Role> rolesToAdd = new ArrayList<>(msg.getMentionedRoles());
            List<Member> membersToAdd = msg.getMentionedMembers().stream()
                    .filter(m -> m.getUser().getIdLong() != m.getJDA().getSelfUser().getIdLong())
                    .collect(Collectors.toList());

            for (String str : content.split("\\p{javaSpaceChar}+")) {
                try {
                    long id = Long.parseUnsignedLong(str);
                    Role roleById = guild.getRoleById(id);
                    if (roleById != null) {
                        rolesToAdd.add(roleById);
                    }
                    Member memberById = guild.getMemberById(id);
                    if (memberById != null) {
                        membersToAdd.add(memberById);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if (rolesToAdd.isEmpty() && membersToAdd.isEmpty()) {
                event.getChannel().sendMessage("Please mention or use an id of at least one role or member that you want to add as admin").queue();
                return;
            }

            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> gs.addAdminRoles(rolesToAdd).addAdminUsers(membersToAdd));
            List<String> added = Stream.concat(
                    membersToAdd.stream().map(m -> (IMentionable) m),
                    rolesToAdd.stream().map(r -> (IMentionable) r)
            ).map(IMentionable::getAsMention).collect(Collectors.toList());

            event.getChannel().sendMessage("Added " + String.join(", ", added) + " as admins.").queue();
        } else if (content.contains("remove admin")) {
            List<Role> rolesToRemove = new ArrayList<>(msg.getMentionedRoles());
            if (!rolesToRemove.isEmpty()) {
                Role r = rolesToRemove.get(0);
                wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), guildSettings -> {
                    if (guildSettings.isAdminRole(r)) {
                        event.getChannel().sendMessage("Removing role " + r.getName() + " " + r.getId() + " from admins.").queue();
                    } else {
                        event.getChannel().sendMessage("Role " + r.getName() + " " + r.getId() + " is not an admin.").queue();
                    }
                    return guildSettings.removeAdminRole(r);
                });
                return;
            }

            List<Member> membersToRemove = msg.getMentionedMembers().stream()
                    .filter(m -> m.getUser().getIdLong() != m.getJDA().getSelfUser().getIdLong())
                    .collect(Collectors.toList());

            if (!membersToRemove.isEmpty()) {
                Member m = membersToRemove.get(0);
                wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), guildSettings -> {
                    if (guildSettings.isAdminUser(m)) {
                        event.getChannel().sendMessage("Removing member " + m.getEffectiveName() + " " + m.getUser().getId() + " from admins.").queue();
                    } else {
                        event.getChannel().sendMessage("Member " + m.getEffectiveName() + " " + m.getUser().getIdLong() + " is not an admin.").queue();
                    }
                    return guildSettings.removeAdminUser(m);
                });
                return;
            }

            List<Long> idsToRemove = new ArrayList<>();
            for (String str : content.split("\\p{javaSpaceChar}+")) {
                try {
                    idsToRemove.add(Long.parseUnsignedLong(str));
                } catch (NumberFormatException ignored) {
                }
            }

            if (!idsToRemove.isEmpty()) {
                long id = idsToRemove.get(0);
                wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), guildSettings -> {
                    Role r = guild.getRoleById(id);
                    Member m = guild.getMemberById(id);
                    User u = shardManagerSupp.get().getUserById(id);
                    if (guildSettings.getAdminRoleIds().contains(id)) {
                        String roleName = r != null ? r.getName() : "unknown (role deleted ?)";
                        event.getChannel().sendMessage("Removing role " + roleName + " " + id + " from admins.").queue();
                    } else if (guildSettings.getAdminUserIds().contains(id)) {
                        String memberName = m != null ? m.getEffectiveName() : null;
                        if (memberName == null) {
                            memberName = u != null ? u.getName() : "unknown (member left ?)";
                        }
                        event.getChannel().sendMessage("Removing member " + memberName + " " + id + " from admins.").queue();
                    } else {
                        String message;
                        if (r != null) {
                            message = "Role " + r.getName() + " " + r.getId() + " is not an admin.";
                        } else if (m != null) {
                            message = "Member " + m.getEffectiveName() + " " + m.getUser().getIdLong() + " is not an admin.";
                        } else if (u != null) {
                            message = "User " + u.getName() + " " + u.getIdLong() + " is not an admin.";
                        } else {
                            message = "Neither role nor member with id " + id + " found as admin.";
                        }
                        event.getChannel().sendMessage(message).queue();
                    }

                    return guildSettings.removeAdminUser(id).removeAdminRole(id);
                });
                return;
            }

            //nothing found for the given input
            event.getChannel().sendMessage("Please mention a role or member or use their id.").queue();
        } else if (content.contains("list roles")) {
            StringBuilder sb = new StringBuilder();
            for (Role r : guild.getRoles()) {
                sb.append(r.getId()).append("\t").append(r.getName()).append("\n");
            }

            if (sb.length() == 0) {
                event.getChannel().sendMessage("No roles found for this guild").queue();
            } else {
                event.getChannel().sendMessage("This guild has the following roles:\n```\n" + sb.toString() + "\n```").queue();
            }
        } else if (content.contains("status") || content.contains("help")) {
            String output = "";
            GuildSettings guildSettings = wrapperSupp.get().getOrCreate(GuildSettings.key(guild));

            Long everyoneRoleId = guildSettings.getEveryoneRoleId();
            if (everyoneRoleId != null) {
                output += "Fake `@everyone` role: <@&" + everyoneRoleId + ">\n";
            } else {
                output += "Fake `@everyone` role not configured.\n";
            }

            Long hereRoleId = guildSettings.getHereRoleId();
            if (hereRoleId != null) {
                output += "Fake `@here` role: <@&" + hereRoleId + ">\n";
            } else {
                output += "Fake `@here` role not configured.\n";
            }

            StringBuilder admins = new StringBuilder();
            for (long roleId : guildSettings.getAdminRoleIds()) {
                Role r = guild.getRoleById(roleId);
                admins.append("Role ").append(roleId).append("\t").append(r != null ? r.getName() : "unknown (deleted ?)").append("\n");
            }
            for (long userId : guildSettings.getAdminUserIds()) {
                String name;
                Member m = guild.getMemberById(userId);
                if (m != null) {
                    name = m.getEffectiveName();
                } else {
                    User u = shardManagerSupp.get().getUserById(userId);
                    name = u != null ? u.getName() : "unknown (left ?)";
                }
                admins.append("Member ").append(userId).append("\t").append(name).append("\n");
            }

            output += "\n\nThe botowner and all users with " + Permission.ADMINISTRATOR.getName() + " permissions may control me.\n";
            if (admins.length() == 0) {
                output += "No other admins roles or members configured.\n";
            } else {
                output += "Other admins:\n" + admins.toString() + "\n";
            }

            output += "\n\nCommands: (always mention me)\n\n";
            output += "`set everyone @role`\n\t\tSet the fake `@everyone` role.\n";
            output += "`reset everyone`\n\t\tRemove the fake `@everyone` role.\n";
            output += "`set here @role`\n\t\tSet the fake `@here` role.\n";
            output += "`reset here`\n\t\tRemove the fake `@here` role.\n";
            output += "`add admin @role or @member or id`\n\t\tAdd admins for this guild.\n";
            output += "`remove admin @role or @member or id`\n\t\tRemove admins for this guild.\n";
            output += "`list roles`\n\t\tList available roles in this guild with ids.\n";
            output += "`status` or `help`\n\t\tShow current config and command help.\n";
            event.getChannel().sendMessage(output).queue();
        }
    }

    public static boolean isAdmin(DatabaseWrapper dbWrapper, Member member) {
        ApplicationInfo appInfo = Main.APP_INFO.get(Main.class, __ -> member.getJDA().asBot().getApplicationInfo().complete());
        //bot owner?
        //noinspection SimplifiableIfStatement
        if (appInfo != null && appInfo.getOwner().getIdLong() == member.getUser().getIdLong()) {
            return true;
        }

        return member.isOwner()
                || member.hasPermission(Permission.ADMINISTRATOR)
                || dbWrapper.getOrCreate(GuildSettings.key(member.getGuild())).isAdmin(member);
    }
}
