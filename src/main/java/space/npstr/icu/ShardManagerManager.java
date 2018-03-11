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

package space.npstr.icu;

import net.dv8tion.jda.bot.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.listeners.CommandsListener;
import space.npstr.icu.listeners.EveryoneHereListener;
import space.npstr.icu.listeners.MemberRoleManager;
import space.npstr.icu.listeners.RoleChangesListener;
import space.npstr.icu.listeners.SuspiciousUsersWarner;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Supplier;

/**
 * Created by napster on 13.02.18.
 */
@ThreadSafe
public class ShardManagerManager {

    private static final Logger log = LoggerFactory.getLogger(ShardManagerManager.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;
    @Nullable
    private volatile ShardManager shardManager;
    private final Object shardManagerInitLock = new Object();

    public ShardManagerManager(Supplier<DatabaseWrapper> wrapperSupp) {
        this.wrapperSupp = wrapperSupp;
    }

    public ShardManager getShardManager() {
        ShardManager singleton = shardManager;
        if (singleton == null) {
            synchronized (shardManagerInitLock) {
                singleton = shardManager;
                if (singleton == null) {
                    shardManager = singleton = initShardManager(wrapperSupp, this::getShardManager);
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

    private static ShardManager initShardManager(Supplier<DatabaseWrapper> wrapperSupplier,
                                                 Supplier<ShardManager> shardManagerSupplier) {
        DefaultShardManagerBuilder shardBuilder = new DefaultShardManagerBuilder()
                .setToken(Config.C.discordToken)
                .setGame(Game.watching("you"))
                .addEventListeners(new RoleChangesListener(wrapperSupplier))
                .addEventListeners(new CommandsListener(wrapperSupplier, shardManagerSupplier))
                .addEventListeners(new EveryoneHereListener(wrapperSupplier))
                .addEventListeners(new MemberRoleManager(wrapperSupplier, shardManagerSupplier))
                .addEventListeners(new SuspiciousUsersWarner(wrapperSupplier, shardManagerSupplier))
                .setEnableShutdownHook(false)
                .setAudioEnabled(false);
        try {
            return shardBuilder.build();
        } catch (Exception e) {
            String message = "Failed to build shard manager";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
