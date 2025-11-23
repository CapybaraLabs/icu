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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * Created by napster on 25.01.18.
 */
@Entity
@Table(name = "guild_settings")
public class GuildSettings {

    @Id
    @Column(name = "guild_id", nullable = false)
    protected long guildId;

    @Nullable
    @Column(name = "everyone_role_id", nullable = true)
    private Long everyoneRoleId;

    @Nullable
    @Column(name = "here_role_id", nullable = true)
    private Long hereRoleId;

    @Nullable
    @Column(name = "member_role_id", nullable = true)
    private Long memberRoleId;

    @Column(name = "admin_role_ids", columnDefinition = "bigint[]", nullable = false)
    private Set<Long> adminRoleIds = new HashSet<>();

    @Column(name = "admin_user_ids", columnDefinition = "bigint[]", nullable = false)
    private Set<Long> adminUserIds = new HashSet<>();

    @Nullable
    @Column(name = "reporting_channel_id", nullable = true)
    private Long reportingChannelId;

    @Column(name = "global_bans_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean globalBansEnabled;

    @Nullable
    @Column(name = "log_channel_id", nullable = true)
    private Long logChannelId;

    @Column(name = "ignored_role_ids", columnDefinition = "bigint[]", nullable = false)
    @ColumnDefault("array[]::bigint[]")
    private Set<Long> ignoredRoleIds = new HashSet<>();

    //jpa / database wrapper
    public GuildSettings() {}

    GuildSettings(long guildId) {
        this.guildId = guildId;
    }

    public long getGuildId() {
        return this.guildId;
    }

    @Nullable
    public Long getEveryoneRoleId() {
        return everyoneRoleId;
    }

    public void setEveryoneRole(Role role) {
        this.everyoneRoleId = role.getIdLong();
    }

    public void resetEveryoneRole() {
        this.everyoneRoleId = null;
    }


    @Nullable
    public Long getHereRoleId() {
        return hereRoleId;
    }

    public void setHereRole(Role role) {
        this.hereRoleId = role.getIdLong();
    }

    public void resetHereRole() {
        this.hereRoleId = null;
    }

    @Nullable
    public Long getMemberRoleId() {
        return memberRoleId;
    }

    public void setMemberRole(Role role) {
        this.memberRoleId = role.getIdLong();
    }

    public void resetMemberRole() {
        this.memberRoleId = null;
    }


    public void addAdminRole(Role role) {
        adminRoleIds.add(role.getIdLong());
    }

    public void addAdminRoles(Collection<Role> roles) {
        for (Role role : roles) {
            addAdminRole(role);
        }
    }

    public void removeAdminRole(long roleId) {
        adminRoleIds.remove(roleId);
    }

    public void removeAdminRole(Role role) {
        removeAdminRole(role.getIdLong());
    }

    //use one of the other methods to add / remove admins
    public Collection<Long> getAdminRoleIds() {
        return Collections.unmodifiableCollection(adminRoleIds);
    }


    public void addAdminUser(Member member) {
        adminUserIds.add(member.getUser().getIdLong());
    }

    public void addAdminUsers(Collection<Member> members) {
        for (Member member : members) {
            addAdminUser(member);
        }
    }

    public void removeAdminUser(long userId) {
        adminUserIds.remove(userId);
    }

    public void removeAdminUser(Member member) {
        removeAdminUser(member.getUser().getIdLong());
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

    public void setReportingChannel(MessageChannel reportingChannel) {
        this.reportingChannelId = reportingChannel.getIdLong();
    }

    public void resetReportingChannel() {
        this.reportingChannelId = null;
    }

    public boolean areGlobalBansEnabled() {
        return globalBansEnabled;
    }

    public void setGlobalBansEnabled(boolean globalBansEnabled) {
        this.globalBansEnabled = globalBansEnabled;
    }

    public void enableGlobalBans() {
        setGlobalBansEnabled(true);
    }

    public void disableGlobalBans() {
        setGlobalBansEnabled(false);
    }


    @Nullable
    public Long getLogChannelId() {
        return logChannelId;
    }

    public void setLogChannel(MessageChannel logChannel) {
        this.logChannelId = logChannel.getIdLong();
    }

    public void resetLogChannel() {
        this.logChannelId = null;
    }

    public Collection<Long> getIgnoredRoleIds() {
        return Collections.unmodifiableCollection(ignoredRoleIds);
    }

    public void addIgnoredRole(Role role) {
        this.ignoredRoleIds.add(role.getIdLong());
    }

    public void addIgnoredRoles(Collection<Role> roles) {
        for (Role role : roles) {
            addIgnoredRole(role);
        }
    }

    public void removeIgnoredRoleId(long roleId) {
        this.ignoredRoleIds.remove(roleId);
    }

    public void removeIgnoredRole(Role role) {
        removeIgnoredRoleId(role.getIdLong());
    }

    public boolean isIgnoredRoleId(long roleId) {
        return ignoredRoleIds.contains(roleId);
    }

    public boolean isIgnoredRole(Role role) {
        return isIgnoredRoleId(role.getIdLong());
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof GuildSettings) && ((GuildSettings) obj).guildId == this.guildId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.guildId);
    }
}
