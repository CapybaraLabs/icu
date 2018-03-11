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
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.Main;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        if (content.contains("reset everyone")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), GuildSettings::resetEveryoneRole);
            event.getChannel().sendMessage("Reset the everyone role").queue();
        } else if (content.contains("set everyone")) {
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
        } else if (content.contains("reset here")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), GuildSettings::resetHereRole);
            event.getChannel().sendMessage("Reset the here role").queue();
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
        } else if (content.contains("reset memberrole")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> {
                Long memberRoleId = gs.getMemberRoleId();
                if (memberRoleId != null) {
                    Role current = guild.getRoleById(memberRoleId);
                    if (current != null) {
                        event.getChannel().sendMessage("Old role " + current.getAsMention() + " still in existence." +
                                " You probably want to delete it to avoid users rejoining getting it reassigned, and also to" +
                                " remove it from current holders.").queue();
                    }
                }
                return gs.resetMemberRole();
            });
            event.getChannel().sendMessage("Reset the member role").queue();
        } else if (content.contains("set memberrole")) {
            Role r = null;
            if (msg.getMentionedRoles().isEmpty()) {
                for (String str : content.split("\\p{javaSpaceChar}+")) {
                    try {
                        long roleId = Long.parseUnsignedLong(str);
                        Role role = guild.getRoleById(roleId);
                        if (role != null) {
                            r = role;
                            break;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (r == null) {
                    event.getChannel().sendMessage("Please mention the role you want to set or use its id. Use `list roles` to see all roles in this guild and their ids.").queue();
                    return;
                }
            } else {
                r = msg.getMentionedRoles().get(0);
            }

            Role memberRole = r;

            if (!guild.getSelfMember().canInteract(memberRole)) {
                event.getChannel().sendMessage("I cannot interact with that role. Please choose another one or move my bot role higher.").queue();
                return;
            }

            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> {
                Long memberRoleId = gs.getMemberRoleId();
                if (memberRoleId != null) {
                    Role current = guild.getRoleById(memberRoleId);
                    if (current != null) {
                        event.getChannel().sendMessage("Old role " + current.getAsMention() + " still in existence." +
                                " You probably want to delete it to avoid users rejoining getting it reassigned, and also to" +
                                " remove it from current holders.").queue();
                    }
                }
                return gs.setMemberRole(memberRole);
            });
            event.getChannel().sendMessage("Set up " + memberRole.getAsMention() + " as the member role. All existing" +
                    " and newly joining human users will get it assigned shortly.").queue();
        } else if (content.contains("reset reporting")) {
            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), GuildSettings::resetReportingChannel);
            event.getChannel().sendMessage("Reset the reporting channel").queue();
        } else if (content.contains("set reporting")) {

            TextChannel reportingChannel = msg.getMentionedChannels().stream().findFirst().orElse(msg.getTextChannel());

            if (!reportingChannel.canTalk()) {
                event.getChannel().sendMessage("I can't talk in " + reportingChannel.getAsMention()).queue();
                return;
            }

            wrapperSupp.get().findApplyAndMerge(GuildSettings.key(guild), gs -> gs.setReportingChannel(reportingChannel));
            event.getChannel().sendMessage("Set up " + reportingChannel.getAsMention() + " as the reporting channel 🚔").queue();
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
        } else if (content.contains("add role")) {
            String adjustedContent = content.replace("add role", "");
            //identify user
            Set<User> mentionedUsers = msg.getMentionedMembers().stream()
                    .map(Member::getUser)
                    .filter(user -> !user.isBot())
                    .collect(Collectors.toSet());
            if (mentionedUsers.isEmpty()) {
                for (String str : adjustedContent.split("\\p{javaSpaceChar}+")) {
                    try {
                        long userId = Long.parseUnsignedLong(str);
                        User user = shardManagerSupp.get().getUserById(userId);
                        if (user != null) {
                            mentionedUsers.add(user);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (mentionedUsers.isEmpty()) {
                for (String str : adjustedContent.split("\\p{javaSpaceChar}+")) {
                    String withSpaces = str.replaceAll("_", " ");
                    mentionedUsers.addAll(guild.getMembersByName(str, true).stream()
                            .map(Member::getUser)
                            .collect(Collectors.toSet()));
                    mentionedUsers.addAll(guild.getMembersByName(withSpaces, true).stream()
                            .map(Member::getUser)
                            .collect(Collectors.toSet()));
                    mentionedUsers.addAll(guild.getMembersByNickname(str, true).stream()
                            .map(Member::getUser)
                            .collect(Collectors.toSet()));
                    mentionedUsers.addAll(guild.getMembersByNickname(withSpaces, true).stream()
                            .map(Member::getUser)
                            .collect(Collectors.toSet()));
                }
            }
            if (mentionedUsers.isEmpty()) {
                event.getChannel().sendMessage("Please mention a user or provide their user id anywhere in your message").queue();
                return;
            }
            if (mentionedUsers.size() > 1) {
                String out = "You specified several users. Which one of these did you mean?\n";
                out += String.join("\n", mentionedUsers.stream()
                        .map(user -> user.getAsMention() + " id(" + user.getId() + ")")
                        .collect(Collectors.toSet()));
                event.getChannel().sendMessage(out).queue();
                return;
            }
            User targetUser = mentionedUsers.iterator().next();
            Member targetMember = guild.getMember(targetUser);
            if (targetMember == null) {
                event.getChannel().sendMessage("User " + targetUser.getAsMention() + " is not a member of this guild.").queue();
                return;
            }

            //identify role
            Set<Role> mentionedRoles = new HashSet<>(msg.getMentionedRoles());
            if (mentionedRoles.isEmpty()) {
                for (String str : adjustedContent.split("\\p{javaSpaceChar}+")) {
                    try {
                        long roleId = Long.parseUnsignedLong(str);
                        Role role = shardManagerSupp.get().getRoleById(roleId);
                        if (role != null) {
                            mentionedRoles.add(role);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (mentionedRoles.isEmpty()) {
                for (String str : adjustedContent.split("\\p{javaSpaceChar}+")) {
                    String withSpaces = str.replace("_", " ");
                    mentionedRoles.addAll(guild.getRolesByName(str, true));
                    mentionedRoles.addAll(guild.getRolesByName(withSpaces, true));
                }
            }
            if (mentionedRoles.isEmpty()) {
                event.getChannel().sendMessage("Please mention a role, or provide a role id or the role name anywhere in your message." +
                        "\nYou can see all roles of this guild with `list roles`.").queue();
                return;
            }
            if (mentionedRoles.size() > 1) {
                String out = "You specified several roles. Which one of these did you mean?\n";
                out += String.join("\n", mentionedRoles.stream()
                        .map(role -> role.getAsMention() + " id(" + role.getId() + ")")
                        .collect(Collectors.toSet()));
                event.getChannel().sendMessage(out).queue();
                return;
            }
            Role targetRole = mentionedRoles.iterator().next();
            if (!msg.getMember().canInteract(targetRole)) {
                msg.getChannel().sendMessage("You cannot interact with role " + targetRole.getAsMention()).queue();
                return;
            }
            if (!guild.getSelfMember().canInteract(targetRole)) {
                msg.getChannel().sendMessage("I cannot interact with role " + targetRole.getAsMention()).queue();
                return;
            }

            msg.getChannel().sendMessage("Giving user " + targetMember.getAsMention()
                    + " role " + targetRole.getAsMention()).queue();
            guild.getController().addSingleRoleToMember(targetMember, targetRole).queue(null,
                    onFail -> msg.getChannel().sendMessage(msg.getAuthor().getAsMention() + ", could not give "
                            + targetMember.getAsMention() + " role " + targetRole.getAsMention()).queue()
            );

        } else if (content.contains("list roles")) {
            List<String> out = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (Role r : guild.getRoles()) {
                if (sb.length() > 1900) {
                    out.add(sb.toString());
                    sb = new StringBuilder();
                }
                sb.append(r.getId()).append("\t").append(r.getName()).append("\n");
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }

            if (out.isEmpty()) {
                event.getChannel().sendMessage("No roles found for this guild").queue();
            } else {
                boolean first = true;
                for (String str : out) {
                    String message;
                    if (first) {
                        message = "This guild has the following roles:\n";
                        first = false;
                    } else {
                        message = "";
                    }
                    message += "```\n" + str + "\n```";
                    event.getChannel().sendMessage(message).queue();
                }
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

            Long memberRoleId = guildSettings.getMemberRoleId();
            if (memberRoleId != null) {
                Role memberRole = guild.getRoleById(memberRoleId);
                if (memberRole != null) {
                    output += "Member role: " + memberRole.getAsMention() + "\n";
                } else {
                    output += "Member role configured (id " + memberRoleId + "), but could not be found. Did you delete it?\n";
                }
            } else {
                output += "Member role not configured.\n";
            }

            Long reportingChannelId = guildSettings.getReportingChannelId();
            if (reportingChannelId != null) {
                TextChannel reportingChannel = guild.getTextChannelById(reportingChannelId);
                if (reportingChannel != null) {
                    output += "Reporting channel is " + reportingChannel.getAsMention() + ".\n";
                } else {
                    output += "Reporting channel has been configured with id " + reportingChannelId + ", but it does not exist.\n";
                }
            } else {
                output += "Reporting channel not configured.\n";
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
            output += "`reset everyone`\n\t\tRemove the fake `@everyone` role.\n";
            output += "`set everyone @role`\n\t\tSet the fake `@everyone` role.\n";
            output += "`reset here`\n\t\tRemove the fake `@here` role.\n";
            output += "`set here @role`\n\t\tSet the fake `@here` role.\n";
            output += "`reset memberrole `\n\t\tRemove the member role.\n";
            output += "`set memberrole @role or roleid`\n\t\tSet the member role that every human member will get assigned.\n";
            output += "`add admin @role or @member or id`\n\t\tAdd admins for this guild.\n";
            output += "`remove admin @role or @member or id`\n\t\tRemove admins for this guild.\n";
            output += "`add role [@user | userId | userName | userNickname] [@role | roleId | roleName]`\n\t\tAdd a role to a user\n";
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
