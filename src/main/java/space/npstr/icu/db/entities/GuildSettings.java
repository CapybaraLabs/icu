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

package space.npstr.icu.db.entities;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Type;
import space.npstr.sqlsauce.entities.discord.BaseDiscordGuild;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by napster on 25.01.18.
 */
@Entity
@Table(name = "guild_settings")
@Cacheable(value = true)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "guild_settings")
public class GuildSettings extends BaseDiscordGuild<GuildSettings> {

    @Nullable
    @Column(name = "everyone_role_id", nullable = true)
    private Long everyoneRoleId;

    @Nullable
    @Column(name = "here_role_id", nullable = true)
    private Long hereRoleId;

    @Nullable
    @Column(name = "member_role_id", nullable = true)
    private Long memberRoleId;

    @Type(type = "array-list-long")
    @Column(name = "admin_role_ids", columnDefinition = "bigint[]", nullable = false)
    private ArrayList<Long> adminRoleIds = new ArrayList<>();

    @Type(type = "array-list-long")
    @Column(name = "admin_user_ids", columnDefinition = "bigint[]", nullable = false)
    private ArrayList<Long> adminUserIds = new ArrayList<>();

    @Nullable
    @Column(name = "reporting_channel_id", nullable = true)
    private Long reportingChannelId;

    //jpa / database wrapper
    GuildSettings() {
    }

    public static EntityKey<Long, GuildSettings> key(Guild guild) {
        return EntityKey.of(guild.getIdLong(), GuildSettings.class);
    }


    @Nullable
    public Long getEveryoneRoleId() {
        return everyoneRoleId;
    }

    public GuildSettings setEveryoneRole(Role role) {
        this.everyoneRoleId = role.getIdLong();
        return this;
    }

    public GuildSettings resetEveryoneRole() {
        this.everyoneRoleId = null;
        return this;
    }


    @Nullable
    public Long getHereRoleId() {
        return hereRoleId;
    }

    public GuildSettings setHereRole(Role role) {
        this.hereRoleId = role.getIdLong();
        return this;
    }

    public GuildSettings resetHereRole() {
        this.hereRoleId = null;
        return this;
    }

    @Nullable
    public Long getMemberRoleId() {
        return memberRoleId;
    }

    public GuildSettings setMemberRole(Role role) {
        this.memberRoleId = role.getIdLong();
        return this;
    }

    public GuildSettings resetMemberRole() {
        this.memberRoleId = null;
        return this;
    }


    public GuildSettings addAdminRole(Role role) {
        if (!adminRoleIds.contains(role.getIdLong())) {
            adminRoleIds.add(role.getIdLong());
        }
        return this;
    }

    public GuildSettings addAdminRoles(Collection<Role> roles) {
        GuildSettings result = this;
        for (Role role : roles) {
            result = addAdminRole(role);
        }
        return result;
    }

    public GuildSettings removeAdminRole(long roleId) {
        while (adminRoleIds.contains(roleId)) {
            adminRoleIds.remove(roleId);
        }
        return this;
    }

    public GuildSettings removeAdminRole(Role role) {
        return removeAdminRole(role.getIdLong());
    }

    //use one of the other methods to add / remove admins
    public Collection<Long> getAdminRoleIds() {
        return Collections.unmodifiableCollection(adminRoleIds);
    }


    public GuildSettings addAdminUser(Member member) {
        if (!adminUserIds.contains(member.getUser().getIdLong())) {
            adminUserIds.add(member.getUser().getIdLong());
        }
        return this;
    }

    public GuildSettings addAdminUsers(Collection<Member> members) {
        GuildSettings result = this;
        for (Member member : members) {
            result = addAdminUser(member);
        }
        return result;
    }

    public GuildSettings removeAdminUser(long userId) {
        while (adminUserIds.contains(userId)) {
            adminUserIds.remove(userId);
        }
        return this;
    }

    public GuildSettings removeAdminUser(Member member) {
        return removeAdminUser(member.getUser().getIdLong());
    }

    //use one of the other methods to add / remove admins
    public Collection<Long> getAdminUserIds() {
        return Collections.unmodifiableCollection(adminUserIds);
    }

    //only true if this roles id is explicitly set as admin
    public boolean isAdminRole(Role role) {
        return role.getGuild().getIdLong() == guildId && adminRoleIds.contains(role.getIdLong());
    }

    //only true if this users id is explicitly set as admin
    public boolean isAdminUser(Member member) {
        return member.getGuild().getIdLong() == guildId && adminUserIds.contains(member.getUser().getIdLong());
    }

    //returns true if the member is an admin for this guild as defined by the guild settings
    //a member of another guild can never be an admin
    public boolean isAdmin(Member member) {
        if (member.getGuild().getIdLong() != guildId) return false;

        for (Role r : member.getRoles()) {
            if (adminRoleIds.contains(r.getIdLong())) {
                return true;
            }
        }

        return adminUserIds.contains(member.getUser().getIdLong());
    }

    @Nullable
    public Long getReportingChannelId() {
        return reportingChannelId;
    }

    public GuildSettings setReportingChannel(TextChannel reportingChannel) {
        this.reportingChannelId = reportingChannel.getIdLong();
        return this;
    }

    public GuildSettings resetReportingChannel() {
        this.reportingChannelId = null;
        return this;
    }
}
