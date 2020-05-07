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

import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

/**
 * Created by napster on 13.02.18.
 * <p>
 * Handles assigning the member role. The member role can be configured and should be understodd as the role that every
 * human member of a guild gets assigned
 */
public class MemberRoleManager extends ThreadedListener {

    private static final Logger log = LoggerFactory.getLogger(MemberRoleManager.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final Supplier<ShardManager> shardManagerSupp;

    public MemberRoleManager(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.wrapperSupp = wrapperSupplier;
        this.shardManagerSupp = shardManagerSupplier;

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, MemberRoleManager.class.getSimpleName() + "-worker")
        );

        service.scheduleAtFixedRate(() -> {
            try {
                shardManagerSupp.get().getGuildCache().forEach(guild -> {
                    try {
                        assignMemberRole(guild, guild.getMemberCache().stream());
                    } catch (Exception e) {
                        log.error("Failed to sync member role for guild {}", guild, e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to sync member roles", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        getExecutor(event.getGuild()).execute(() -> guildMemberJoin(event));
    }

    private void guildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        assignMemberRole(guild, Stream.of(event.getMember()));
    }


    private void assignMemberRole(Guild guild, Stream<Member> members) {
        Long roleId = wrapperSupp.get().getOrCreate(GuildSettings.key(guild)).getMemberRoleId();
        if (roleId == null) { //no member role configured
            return;
        }
        Role memberRole = guild.getRoleById(roleId);
        if (memberRole == null) {
            log.warn("Guild {} has a member role configured, but I can't find it", guild);
            return;
        }
        if (!guild.getSelfMember().canInteract(memberRole)) {
            log.warn("Guild {} has a member role configured, but I can't interact with it", guild);
            return;
        }
        members.forEach(member -> {
            try {
                if (member.getUser().isBot() //only humans get the member role
                        || member.getRoles().contains(memberRole)) { //only members missing the role get it
                    return;
                }

                if (guild.getVerificationLevel().getKey() >= Guild.VerificationLevel.HIGH.getKey()) {
                    //only assign to users that are at least 10 minutes in the guild, otherwise it circumvents
                    //the message sending restrictions of the HIGH verification level (users with a role can send messages)
                    if (member.getTimeJoined().isAfter(OffsetDateTime.now().minusMinutes(10))) {
                        return;
                    }
                }

                guild.addRoleToMember(member, memberRole).queue(null, throwable -> {
                    if (throwable instanceof ErrorResponseException) {
                        ErrorResponseException errorResponseException = (ErrorResponseException) throwable;
                        if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                            // workaround for eventual consistency / a possible bug happening since the new
                            // intent system is in use.
                            log.warn("Unloading member {}", member);
                            guild.unloadMember(member.getUser().getIdLong());
                            return;
                        }
                    }
                    log.error("Could not assign member role to user {}", member, throwable);
                });
            } catch (Exception e) {
                log.error("Could not assign member role to user {}", member, e);
            }
        });
    }
}
