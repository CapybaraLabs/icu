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

package space.npstr.icu.db.entities;

import net.dv8tion.jda.core.entities.User;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import space.npstr.sqlsauce.entities.discord.BaseDiscordUser;
import space.npstr.sqlsauce.fp.types.EntityKey;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Created by napster on 10.03.18.
 * <p>
 * Global bans by Napster / botowner
 */
@Entity
@Table(name = "global_bans")
@Cacheable(value = true)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "global_bans")
public class GlobalBan extends BaseDiscordUser<GlobalBan> {

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason = "No reason provided";

    @Column(name = "created", nullable = false)
    private long created = System.currentTimeMillis();

    //JPA / wrapper
    GlobalBan() {
    }

    public static EntityKey<Long, GlobalBan> key(User user) {
        return EntityKey.of(user.getIdLong(), GlobalBan.class);
    }

    public String getReason() {
        return reason;
    }

    public GlobalBan setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public long getCreated() {
        return created;
    }
}
