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
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Created by napster on 10.03.18.
 * <p>
 * Global bans by Napster / botowner
 */
@Entity
@Table(name = "global_bans")
public class GlobalBan {

    @Id
    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason = "No reason provided";

    @Column(name = "created", nullable = false)
    private long created = System.currentTimeMillis();

    //JPA / wrapper
    GlobalBan() {}

    GlobalBan(long userId) {
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getCreated() {
        return created;
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof GlobalBan) && ((GlobalBan) obj).userId == this.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.userId);
    }
}
