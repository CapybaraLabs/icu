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

package space.npstr.icu.db.entities;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.entities.MemberComposite;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.hibernate.types.BasicType;

/**
 * Created by napster on 27.12.17.
 */
@Entity
@Table(name = "member_roles")
public class MemberRoles extends SaucedEntity<MemberComposite, MemberRoles> {

    private static final Logger log = LoggerFactory.getLogger(MemberRoles.class);

    @SuppressWarnings("NullableProblems") //never null if correctly initialized by Hibernate / sqlsauce
    @EmbeddedId
    private MemberComposite id;

    @Type(type = "hash-set-basic")
    @BasicType(Long.class)
    @Column(name = "role_ids", columnDefinition = "bigint[]")
    private HashSet<Long> roleIds = new HashSet<>();

    @Nullable
    @Column(name = "nickname", columnDefinition = "text", nullable = true)
    private String nickname;

    //for jpa / database wrapper
    MemberRoles() {
    }

    public static EntityKey<MemberComposite, MemberRoles> key(Member member) {
        return EntityKey.of(new MemberComposite(member), MemberRoles.class);
    }

    public static EntityKey<MemberComposite, MemberRoles> key(Guild guild, User user) {
        return EntityKey.of(new MemberComposite(guild, user), MemberRoles.class);
    }

    @Override
    public MemberRoles setId(MemberComposite id) {
        this.id = id;
        return this;
    }

    @Override
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

    @CheckReturnValue
    public MemberRoles addRoleId(long roleId) {
        roleIds.add(roleId);
        return this;
    }

    @CheckReturnValue
    public MemberRoles addRole(Role role) {
        if (role.isManaged()) {
            return this; //dont touch managed roles, this is not our responsibility
        }
        return addRoleId(role.getIdLong());
    }

    @CheckReturnValue
    public MemberRoles addRoles(Collection<Role> roles) {
        MemberRoles result = this;
        for (Role role : roles) {
            result = addRole(role);
        }
        return result;
    }

    @CheckReturnValue
    public MemberRoles removeRoleId(long roleId) {
        roleIds.remove(roleId);
        return this;
    }

    @CheckReturnValue
    public MemberRoles removeRole(Role role) {
        return removeRoleId(role.getIdLong());
    }

    @CheckReturnValue
    public MemberRoles removeRoles(Collection<Role> roles) {
        MemberRoles result = this;
        for (Role role : roles) {
            result = removeRole(role);
        }
        return result;
    }

    @CheckReturnValue
    public MemberRoles setRoleIds(Collection<Long> roleIds) {
        this.roleIds.clear();
        this.roleIds.addAll(roleIds);
        return this;
    }

    @CheckReturnValue
    public MemberRoles setRoles(Collection<Role> roles) {
        return setRoleIds(roles.stream()
                .filter(role -> !role.isManaged()) //dont touch managed roles, this is not our responsibility
                .map(ISnowflake::getIdLong).collect(Collectors.toSet()));
    }

    @Nullable
    public String getNickname() {
        return nickname;
    }

    @CheckReturnValue
    public MemberRoles setNickname(@Nullable String nickname) {
        this.nickname = nickname;
        return this;
    }

    @CheckReturnValue
    public MemberRoles set(Member member) {
        return setRoles(member.getRoles())
                .setNickname(member.getNickname());
    }
}
