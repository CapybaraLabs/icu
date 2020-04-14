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

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.icu.db.entities.MemberRoles;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.MemberComposite;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by napster on 27.12.17.
 */
public class RoleChangesListener extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(RoleChangesListener.class);


    private final Supplier<DatabaseWrapper> wrapperSupp;


    public RoleChangesListener(Supplier<DatabaseWrapper> wrapperSupplier) {
        this.wrapperSupp = wrapperSupplier;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            for (Role role : event.getRoles()) {
                log.debug("Role {} added to user {}", role, event.getMember());
            }
            updateMember(event.getMember());
        });
    }

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            log.debug("Nickname set from {} to {} for user {}", event.getOldNickname(), event.getNewNickname(), event.getMember());
            updateMember(event.getMember());
        });
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            for (Role role : event.getRoles()) {
                log.debug("Role {} removed from user {}", role, event.getMember());
            }
            updateMember(event.getMember());
        });
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        //according to Minn, this event reliably contains the roles of a member
        getExecutor(event.getGuild()).execute(() -> {
            Member member = event.getMember();
            if (member == null) {
                return;
            }
            Collection<Role> roles = member.getRoles();
            log.debug("User {} left guild {}, with roles: {}", member, event.getGuild(), roles.isEmpty() ? "no roles" :
                    roles.stream().map(Object::toString).collect(Collectors.joining(", ")));
            updateMember(member);
        });
    }


    private void updateMember(Member member) {
        wrapperSupp.get().findApplyAndMerge(MemberRoles.key(member), mr -> mr.set(member));
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            EntityKey<MemberComposite, MemberRoles> key = EntityKey.of(new MemberComposite(event.getMember()), MemberRoles.class);
            MemberRoles memberRoles = wrapperSupp.get().getOrCreate(key);
            Collection<Role> roles = memberRoles.getRoles(__ -> event.getGuild());
            String storedNick = memberRoles.getNickname();
            log.debug("User {} joined guild {}, restoring nickname {} and roles: {}",
                    event.getMember(), event.getGuild(), storedNick,
                    roles.isEmpty()
                            ? "no roles"
                            : roles.stream().map(Object::toString).collect(Collectors.joining(", ")));

            Member self = event.getGuild().getSelfMember();
            if (storedNick != null && !storedNick.isEmpty()
                    && self.hasPermission(Permission.NICKNAME_MANAGE)
                    && self.canInteract(event.getMember())) {
                try {
                    event.getGuild().modifyNickname(event.getMember(), storedNick).queue();
                } catch (Exception e) {
                    log.error("Failed to set nickname {} for user {}", storedNick, event.getMember());
                }
            }

            var guildSettingsKey = EntityKey.of(event.getGuild().getIdLong(), GuildSettings.class);
            GuildSettings guildSettings = wrapperSupp.get().getOrCreate(guildSettingsKey);
            roles = roles.stream().filter(
                    role -> {
                        if (role.isManaged()) {
                            log.info("Ignoring role {} on member {} in guild {} because it is managed", role, event.getMember(), event.getGuild());
                            return false;
                        }
                        if (!self.canInteract(role)) {
                            log.info("Ignoring role {} on member {} in guild {} because I can't interact with it", role, event.getMember(), event.getGuild());
                            return false;
                        }
                        if (guildSettings.isIgnoredRole(role)) {
                            log.info("Ignoring role {} on member {} in guild {} because it is ignored according to guild settings", role, event.getMember(), event.getGuild());
                            return false;
                        }
                        return true;
                    }
            ).collect(Collectors.toSet());

            // we space out adding roles like this, because bulk changes lead to race conditions with other bots who
            // apply roles on join (think dyno's managed mute role). the average user, especially any malicious one,
            // probably wont have many roles, so we can accept the higher ratelimit exhaustion as a trade off for safety
            // of a race condition
            roles.forEach(role ->
                    event.getGuild().addRoleToMember(event.getMember(), role).queue()
            );
        });
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            Stream<Transfiguration<MemberComposite, MemberRoles>> stream = event.getGuild().getMemberCache().stream()
                    .map(member -> Transfiguration.of(MemberRoles.key(member), memberRole -> memberRole.set(member)));

            wrapperSupp.get().findApplyAndMergeAll(stream);
        });
    }

    @Override
    public void onReady(ReadyEvent event) {
        DEFAULT_EXEC.execute(() -> {
            Stream<Transfiguration<MemberComposite, MemberRoles>> stream = event.getJDA().getGuildCache().stream()
                    .flatMap(guild -> guild.getMemberCache().stream())
                    .map(member -> Transfiguration.of(MemberRoles.key(member), memberRole -> memberRole.set(member)));

            wrapperSupp.get().findApplyAndMergeAll(stream);
        });
    }
}
