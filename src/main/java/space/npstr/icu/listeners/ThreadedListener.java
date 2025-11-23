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

package space.npstr.icu.listeners;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

/**
 * Created by napster on 13.02.18.
 */
public abstract class ThreadedListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ThreadedListener.class);

    private static final Thread.UncaughtExceptionHandler exceptionHandler
            = (t, e) -> log.error("Exception in thread {}", t.getName(), e);

    //to be used for non-guild events
    protected static final ExecutorService DEFAULT_EXEC = provideExecutor(0L);

    //per guild
    private final LoadingCache<Long, ExecutorService> EXECUTORS = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .removalListener((Long key, ExecutorService value, RemovalCause cause) -> {
                if (value != null) {
                    value.shutdown();
                }
            })
            .build(ThreadedListener::provideExecutor);


    /**
     * Creates a new Executor
     */
    private static ExecutorService provideExecutor(long guildId) {
        return Executors.newSingleThreadExecutor(
                r -> {
                    Thread t = new Thread(r, "threaded-listener-" + guildId);
                    t.setUncaughtExceptionHandler(exceptionHandler);
                    return t;
                });
    }


    /**
     * Get an Executor from the cache. Returns a shared default executor for null guilds
     */
    protected ExecutorService getExecutor(@Nullable Guild guild) {
        if (guild == null) {
            return DEFAULT_EXEC;
        }
        ExecutorService executor = EXECUTORS.get(guild.getIdLong());
        if (executor != null) {
            return executor;
        } else {
            log.warn("Cached executor for guild {} is somehow null, returning default executor", guild);
            return DEFAULT_EXEC;
        }
    }
}
