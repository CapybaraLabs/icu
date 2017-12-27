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

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.entities.MemberComposite;
import space.npstr.sqlsauce.entities.SaucedEntity;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by napster on 27.12.17.
 */
@Entity
@Table(name = "member_roles")
public class MemberRoles extends SaucedEntity<MemberComposite, MemberRoles> {

    private static final Logger log = LoggerFactory.getLogger(MemberRoles.class);

    @EmbeddedId
    private MemberComposite id;

    @Nonnull
    @Type(type = "array-list-long")
    @Column(name = "role_ids", columnDefinition = "bigint[]")
    private ArrayList<Long> roleIds = new ArrayList<>();

    //for jpa / database wrapper
    public MemberRoles() {
    }

    public static EntityKey<MemberComposite, MemberRoles> key(@Nonnull Member member) {
        return EntityKey.of(new MemberComposite(member), MemberRoles.class);
    }

    @Nonnull
    @Override
    public MemberRoles setId(@Nonnull MemberComposite id) {
        this.id = id;
        return this;
    }

    @Nonnull
    @Override
    public MemberComposite getId() {
        return this.id;
    }

    @Nonnull
    public Collection<Long> getRoleIds() {
        return Collections.unmodifiableList(roleIds);
    }

    @Nonnull
    public Collection<Role> getRoles(@Nonnull Function<Long, Guild> guildProvider) {
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

    @Nonnull
    @CheckReturnValue
    public MemberRoles addRoleId(long roleId) {
        if (!roleIds.contains(roleId)) {
            roleIds.add(roleId);
        }
        return this;
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles addRole(@Nonnull Role role) {
        if (role.isManaged()) {
            return this; //dont touch managed roles, this is not our responsibility
        }
        return addRoleId(role.getIdLong());
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles addRoles(@Nonnull Collection<Role> roles) {
        MemberRoles result = this;
        for (Role role : roles) {
            result = addRole(role);
        }
        return result;
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles removeRoleId(long roleId) {
        while (roleIds.contains(roleId)) {
            roleIds.remove(roleId);
        }
        return this;
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles removeRole(@Nonnull Role role) {
        return removeRoleId(role.getIdLong());
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles removeRoles(@Nonnull Collection<Role> roles) {
        MemberRoles result = this;
        for (Role role : roles) {
            result = removeRole(role);
        }
        return result;
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles setRoleIds(@Nonnull Collection<Long> roleIds) {
        this.roleIds.clear();
        this.roleIds.addAll(roleIds);
        return this;
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles setRoles(@Nonnull Collection<Role> roles) {
        return setRoleIds(roles.stream()
                .filter(role -> !role.isManaged()) //dont touch managed roles, this is not our responsibility
                .map(ISnowflake::getIdLong).collect(Collectors.toSet()));
    }

    @Nonnull
    @CheckReturnValue
    public MemberRoles set(@Nonnull Member member) {
        return setRoles(member.getRoles());
    }
}
