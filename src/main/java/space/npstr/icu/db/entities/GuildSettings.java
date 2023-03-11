/*
 * Copyright (C) 2018 - 2023 Dennis Neufeld
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
import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Type;
import space.npstr.sqlsauce.entities.discord.BaseDiscordGuild;
import space.npstr.sqlsauce.fp.types.EntityKey;
import space.npstr.sqlsauce.hibernate.types.BasicType;

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

    @Type(type = "hash-set-basic")
    @BasicType(Long.class)
    @Column(name = "admin_role_ids", columnDefinition = "bigint[]", nullable = false)
    private HashSet<Long> adminRoleIds = new HashSet<>();

    @Type(type = "hash-set-basic")
    @BasicType(Long.class)
    @Column(name = "admin_user_ids", columnDefinition = "bigint[]", nullable = false)
    private HashSet<Long> adminUserIds = new HashSet<>();

    @Nullable
    @Column(name = "reporting_channel_id", nullable = true)
    private Long reportingChannelId;

    @Column(name = "global_bans_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean globalBansEnabled;

    @Nullable
    @Column(name = "log_channel_id", nullable = true)
    private Long logChannelId;

    @Type(type = "hash-set-basic")
    @BasicType(Long.class)
    @Column(name = "ignored_role_ids", columnDefinition = "bigint[]", nullable = false)
    @ColumnDefault("array[]::bigint[]")
    private HashSet<Long> ignoredRoleIds = new HashSet<>();

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
        adminRoleIds.add(role.getIdLong());
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
        adminRoleIds.remove(roleId);
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
        adminUserIds.add(member.getUser().getIdLong());
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
        adminUserIds.remove(userId);
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

    public GuildSettings setReportingChannel(MessageChannel reportingChannel) {
        this.reportingChannelId = reportingChannel.getIdLong();
        return this;
    }

    public GuildSettings resetReportingChannel() {
        this.reportingChannelId = null;
        return this;
    }

    public boolean areGlobalBansEnabled() {
        return globalBansEnabled;
    }

    public GuildSettings setGlobalBansEnabled(boolean globalBansEnabled) {
        this.globalBansEnabled = globalBansEnabled;
        return this;
    }

    public GuildSettings enableGlobalBans() {
        return setGlobalBansEnabled(true);
    }

    public GuildSettings disableGlobalBans() {
        return setGlobalBansEnabled(false);
    }


    @Nullable
    public Long getLogChannelId() {
        return logChannelId;
    }

    public GuildSettings setLogChannel(MessageChannel logChannel) {
        this.logChannelId = logChannel.getIdLong();
        return this;
    }

    public GuildSettings resetLogChannel() {
        this.logChannelId = null;
        return this;
    }

    public Collection<Long> getIgnoredRoleIds() {
        return Collections.unmodifiableCollection(ignoredRoleIds);
    }

    public GuildSettings addIgnoredRole(Role role) {
        this.ignoredRoleIds.add(role.getIdLong());
        return this;
    }

    public GuildSettings addIgnoredRoles(Collection<Role> roles) {
        GuildSettings result = this;
        for (Role role : roles) {
            result = addIgnoredRole(role);
        }
        return result;
    }

    public GuildSettings removeIgnoredRoleId(long roleId) {
        this.ignoredRoleIds.remove(roleId);
        return this;
    }

    public GuildSettings removeIgnoredRole(Role role) {
        return removeIgnoredRoleId(role.getIdLong());
    }

    public boolean isIgnoredRoleId(long roleId) {
        return ignoredRoleIds.contains(roleId);
    }

    public boolean isIgnoredRole(Role role) {
        return isIgnoredRoleId(role.getIdLong());
    }
}
