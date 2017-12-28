/*
 * Copyright (C) 2017 Dennis Neufeld
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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.db.entities.MemberRoles;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.entities.MemberComposite;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.fp.types.Transfiguration;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by napster on 27.12.17.
 */
public class RoleChangesListener extends ListenerAdapter {

    @Nonnull
    private static final Logger log = LoggerFactory.getLogger(RoleChangesListener.class);

    @Nonnull
    private static Thread.UncaughtExceptionHandler exceptionHandler
            = (t, e) -> log.error("Exception in thread {}", t.getName(), e);

    @Nonnull
    private static ExecutorService DEFAULT_EXEC = provideExecutor(0L);

    //per guild
    @Nonnull
    private final LoadingCache<Long, ExecutorService> EXECUTORS = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .removalListener((Long key, ExecutorService value, RemovalCause cause) -> {
                if (value != null) {
                    value.shutdown();
                }
            })
            .build(RoleChangesListener::provideExecutor);
    @Nonnull
    private final DatabaseWrapper dbWrapper;


    /**
     * Creates a new Executor
     */
    @Nonnull
    private static ExecutorService provideExecutor(long guildId) {
        return Executors.newSingleThreadExecutor(
                r -> {
                    Thread t = new Thread(r, "role-changes-listener-" + guildId);
                    t.setUncaughtExceptionHandler(exceptionHandler);
                    return t;
                });
    }


    public RoleChangesListener(@Nonnull DatabaseWrapper dbWrapper) {
        this.dbWrapper = dbWrapper;
    }


    /**
     * Get an Executor from the cache.
     */
    @Nonnull
    private ExecutorService getExecutor(@Nonnull Guild guild) {
        ExecutorService executor = EXECUTORS.get(guild.getIdLong());
        if (executor != null) {
            return executor;
        } else {
            log.warn("Cached executor for guild {} is somehow null, returning defualt executor", guild);
            return DEFAULT_EXEC;
        }
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            for (Role role : event.getRoles()) {
                log.debug("Role {} added to user {}", role, event.getMember());
            }
            updateRoles(event.getMember());
        });
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            for (Role role : event.getRoles()) {
                log.debug("Role {} removed from user {}", role, event.getMember());
            }
            updateRoles(event.getMember());
        });
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        //according to Minn, this event reliably contains the roles of a member
        getExecutor(event.getGuild()).execute(() -> {
            Collection<Role> roles = event.getMember().getRoles();
            log.debug("User {} left guild {}, with roles: {}", event.getMember(), event.getGuild(), roles.isEmpty() ? "no roles" :
                    String.join(", ", roles.stream().map(Object::toString).collect(Collectors.toList())));
            updateRoles(event.getMember());
        });
    }


    private void updateRoles(@Nonnull Member member) {
        dbWrapper.findApplyAndMerge(MemberRoles.key(member), mr -> mr.set(member));
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            EntityKey<MemberComposite, MemberRoles> key = EntityKey.of(new MemberComposite(event.getMember()), MemberRoles.class);
            MemberRoles memberRoles = dbWrapper.getOrCreate(key);
            Collection<Role> roles = memberRoles.getRoles(__ -> event.getGuild());
            log.debug("User {} joined guild {}, restoring roles: {}", event.getMember(), event.getGuild(), roles.isEmpty() ? "no roles" :
                    String.join(", ", roles.stream().map(Object::toString).collect(Collectors.toList())));

            Member self = event.getGuild().getSelfMember();
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
                        return true;
                    }
            ).collect(Collectors.toSet());

            event.getGuild().getController().addRolesToMember(event.getMember(), roles).queue();
        });
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> {
            Stream<Transfiguration<MemberComposite, MemberRoles>> stream = event.getGuild().getMemberCache().stream()
                    .map(member -> Transfiguration.of(MemberRoles.key(member), memberRole -> memberRole.set(member)));

            dbWrapper.findApplyAndMergeAll(stream);
        });
    }

    @Override
    public void onReady(ReadyEvent event) {
        DEFAULT_EXEC.execute(() -> {
            Stream<Transfiguration<MemberComposite, MemberRoles>> stream = event.getJDA().getGuildCache().stream()
                    .flatMap(guild -> guild.getMemberCache().stream())
                    .map(member -> Transfiguration.of(MemberRoles.key(member), memberRole -> memberRole.set(member)));

            dbWrapper.findApplyAndMergeAll(stream);
        });
    }
}
