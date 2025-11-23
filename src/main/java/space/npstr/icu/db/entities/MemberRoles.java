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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

/**
 * Created by napster on 27.12.17.
 */
@Entity
@Table(name = "member_roles")
public class MemberRoles {

    private static final Logger log = LoggerFactory.getLogger(MemberRoles.class);

    @EmbeddedId
    private MemberComposite id;

    @Column(name = "role_ids", columnDefinition = "bigint[]")
    private Set<Long> roleIds = new HashSet<>();

    @Nullable
    @Column(name = "nickname", columnDefinition = "text", nullable = true)
    private String nickname;

    //for jpa / database wrapper
    public MemberRoles() {}

    MemberRoles(MemberComposite id) {
        this.id = id;
    }

    public static MemberComposite key(Member member) {
        return new MemberComposite(member);
    }

    public static MemberComposite key(Guild guild, User user) {
        return new MemberComposite(guild, user);
    }

    public MemberComposite getId() {
        return this.id;
    }

    public Collection<Long> getRoleIds() {
        return Collections.unmodifiableCollection(roleIds);
    }

    public Collection<Role> getRoles(Function<Long, Guild> guildProvider) {
        Guild g = guildProvider.apply(id.getGuildId());
        if (g == null) {
            log.warn("Guild provider returned a null guild for id {}, could not look up roles for user {}", id.getGuildId(), id.getUserId());
            return Collections.emptyList();
        }
        return roleIds.stream()
                .map(g::getRoleById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public void addRoleId(long roleId) {
        roleIds.add(roleId);
    }

    public void addRole(Role role) {
        if (role.isManaged()) {
            return; //dont touch managed roles, this is not our responsibility
        }
        addRoleId(role.getIdLong());
    }

    public void addRoles(Collection<Role> roles) {
        for (Role role : roles) {
            addRole(role);
        }
    }

    public void removeRoleId(long roleId) {
        roleIds.remove(roleId);
    }

    public void removeRole(Role role) {
        removeRoleId(role.getIdLong());
    }

    public void removeRoles(Collection<Role> roles) {
        for (Role role : roles) {
            removeRole(role);
        }
    }

    public void setRoleIds(Collection<Long> roleIds) {
        this.roleIds.clear();
        this.roleIds.addAll(roleIds);
    }

    public void setRoles(Collection<Role> roles) {
        setRoleIds(roles.stream()
            .filter(role -> !role.isManaged()) //dont touch managed roles, this is not our responsibility
            .map(ISnowflake::getIdLong).collect(Collectors.toSet()));
    }

    @Nullable
    public String getNickname() {
        return nickname;
    }

    public void setNickname(@Nullable String nickname) {
        this.nickname = nickname;
    }

    public void set(Member member) {
        setRoles(member.getRoles());
        setNickname(member.getNickname());
    }


    @Embeddable
    public static class MemberComposite implements Serializable {

        @Column(name = "guild_id", nullable = false)
        private long guildId;

        @Column(name = "user_id", nullable = false)
        private long userId;

        //for jpa & the database wrapper
        public MemberComposite() {
        }

        public MemberComposite(Member member) {
            this(member.getGuild(), member.getUser());
        }

        public MemberComposite(Guild guild, User user) {
            this(guild.getIdLong(), user.getIdLong());
        }

        public MemberComposite(long guildId, long userId) {
            this.guildId = guildId;
            this.userId = userId;
        }

        public long getGuildId() {
            return guildId;
        }

        public void setGuildId(long guildId) {
            this.guildId = guildId;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(guildId, userId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemberComposite other)) return false;
            return this.guildId == other.guildId && this.userId == other.userId;
        }

        @Override
        public String toString() {
            return MemberComposite.class.getSimpleName() + String.format("(G %s, U %s)", guildId, userId);
        }
    }
}
