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

package space.npstr.icu;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 16.05.18.
 */
public class AuditLogUtil {

    private static final Logger log = LoggerFactory.getLogger(AuditLogUtil.class);

    private static final long TEN_SECONDS = 10 * 1000;

    //static util class
    private AuditLogUtil() {}


    @CheckReturnValue
    public static Optional<String> getBanReason(Guild guild, User user, OffsetDateTime banTime) {
        return getReasonFromBanlist(guild, user)
                .or(() -> getAuditLogEntry(guild, user, ActionType.BAN, banTime)
                        .map(AuditLogEntry::getReason));
    }

    //return the person that unbanned a user
    @CheckReturnValue
    public static Optional<User> getUnbanner(Guild guild, User user, OffsetDateTime unbanTime) {
        return getAuditLogEntry(guild, user, ActionType.UNBAN, unbanTime)
                .map(AuditLogEntry::getUser);
    }

    @CheckReturnValue
    public static Optional<AuditLogEntry> getKickEntry(Guild guild, User user, OffsetDateTime leaveTime) {
        return getAuditLogEntry(guild, user, ActionType.KICK, leaveTime);
    }

    @CheckReturnValue
    private static Optional<String> getReasonFromBanlist(Guild guild, User user) {
        if (guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            try {
                return Optional.ofNullable(guild.retrieveBan(user).submit().join().getReason());
            } catch (Exception e) {
                if (!(e instanceof ErrorResponseException)
                        || ((ErrorResponseException) e).getErrorResponse() != ErrorResponse.UNKNOWN_BAN) {
                    log.error("Failed to get ban reason for banned user {} of guild {} through the ban list",
                        user, guild, e);
                }
            }
        }
        return Optional.empty();
    }

    //get a reason from the audit log of a guild for the provided user and actiontype
    //returns empty if we are missing the rights to do this
    @CheckReturnValue
    private static Optional<AuditLogEntry> getAuditLogEntry(Guild guild, User user, ActionType actionType, OffsetDateTime eventTime) {
        if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            try {
                return guild.retrieveAuditLogs().type(actionType).submit().get(5, TimeUnit.MINUTES)
                        .stream()
                        .filter(entry -> entry.getType() == actionType)
                        .filter(entry -> entry.getTargetIdLong() == user.getIdLong())
                        .filter(entry -> isInTimeRange(entry.getTimeCreated(), eventTime, TEN_SECONDS))
                        .findFirst();
            } catch (Exception e) {
                log.error("Failed to get reason for action {} against user {} of guild {} through the audit logs",
                        actionType, user, guild, e);
            }
        }
        return Optional.empty();
    }

    private static boolean isInTimeRange(OffsetDateTime a, OffsetDateTime b, long maxRange) {
        long diff = a.toEpochSecond() - b.toEpochSecond();
        if (diff < 0) diff *= -1;
        return diff <= maxRange;
    }
}
