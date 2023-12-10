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

package space.npstr.icu;

import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import space.npstr.icu.listeners.ThreadedListener;

/**
 * Created by napster on 13.02.18.
 * <p>
 * This class's name is totally not a meme.
 */
@Component
public class ShardManagerManager {

    private static final Logger log = LoggerFactory.getLogger(ShardManagerManager.class);

    private final List<ThreadedListener> listeners;
    @Nullable
    private volatile ShardManager shardManager;
    private final Object shardManagerInitLock = new Object();

    public ShardManagerManager(List<ThreadedListener> listeners) {
        this.listeners = listeners;
    }

    public ShardManager getShardManager() {
        ShardManager singleton = shardManager;
        if (singleton == null) {
            synchronized (shardManagerInitLock) {
                singleton = shardManager;
                if (singleton == null) {
                    shardManager = singleton = initShardManager(listeners);
                }
            }
        }
        return singleton;
    }

    public void shutdown() {
        synchronized (shardManagerInitLock) {
            ShardManager sm = this.shardManager;
            if (sm != null) {
                sm.shutdown();
            }
        }
    }

    private static ShardManager initShardManager(Collection<? extends ListenerAdapter> listeners) {
        DefaultShardManagerBuilder shardBuilder = DefaultShardManagerBuilder
            .createDefault(Config.C.discordToken)
            .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .setActivity(Activity.watching("you"))
            .addEventListeners(listeners.toArray())
            .setEnableShutdownHook(false);
        try {
            return shardBuilder.build();
        } catch (Exception e) {
            String message = "Failed to build shard manager";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
