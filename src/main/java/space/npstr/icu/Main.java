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

package space.npstr.icu;

import ch.qos.logback.classic.LoggerContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.bot.entities.ApplicationInfo;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.JDAInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.info.AppInfo;
import space.npstr.icu.info.GitRepoState;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 27.12.17.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final DbManager dbManager;
    private final ShardManagerManager shardManagerManager;
    private final GlobalBanSync globalBanSync;

    //use something constant as the key, like Main.class
    public static final Cache<Object, ApplicationInfo> APP_INFO = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public static void main(final String[] args) {
        //just post the info to the console
        if (args.length > 0 &&
                (args[0].equalsIgnoreCase("-v")
                        || args[0].equalsIgnoreCase("--version")
                        || args[0].equalsIgnoreCase("-version"))) {
            System.out.println("Version flag detected. Printing version info, then exiting.");
            System.out.println(getVersionInfo());
            System.out.println("Version info printed, exiting.");
            System.exit(0);
        }

        new Main();
    }

    private Main() {
        Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
        log.info(getVersionInfo());

        dbManager = new DbManager();
        shardManagerManager = new ShardManagerManager(this::getDbWrapper);
        globalBanSync = new GlobalBanSync(this::getDbWrapper, this::getShardManager);

        getShardManager();//call this to start the JDA loops
    }

    public ShardManager getShardManager() {
        return shardManagerManager.getShardManager();
    }

    public DatabaseWrapper getDbWrapper() {
        return dbManager.getDefaultDbWrapper();
    }

    @SuppressWarnings("ConstantConditions") //it can be null during init
    @Nullable
    private ShardManagerManager getShardManagerManager() {
        return shardManagerManager;
    }

    @SuppressWarnings("ConstantConditions") //it can be null during init
    @Nullable
    private DbManager getDbManager() {
        return dbManager;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final Thread SHUTDOWN_HOOK = new Thread(() -> {
        log.info("Shutdown hook triggered!");
        //okHttpClient claims that a shutdown isn't necessary

        //shutdown JDA
        log.info("Shutting down shards");
        ShardManagerManager smm = getShardManagerManager();
        if (smm != null) smm.shutdown();

        //shutdown DB
        log.info("Shutting down database");
        DbManager dbm = getDbManager();
        if (dbm != null) dbm.shutdown();

        //shutdown logback logger
        log.info("Shutting down logger :rip:");
        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }, "shutdown-hook");

    public static final DateTimeFormatter TIME_IN_CENTRAL_EUROPE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z")
            .withZone(ZoneId.of("Europe/Berlin"));

    public static String asTimeInCentralEurope(final long epochMillis) {
        return TIME_IN_CENTRAL_EUROPE.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String getVersionInfo() {

        return "\n\n"
                + "\n\tVersion:       " + AppInfo.getAppInfo().VERSION
                + "\n\tBuild:         " + AppInfo.getAppInfo().BUILD_NUMBER
                + "\n\tBuild time:    " + asTimeInCentralEurope(AppInfo.getAppInfo().BUILD_TIME)
                + "\n\tCommit:        " + GitRepoState.getGitRepositoryState().commitIdAbbrev + " (" + GitRepoState.getGitRepositoryState().branch + ")"
                + "\n\tCommit time:   " + asTimeInCentralEurope(GitRepoState.getGitRepositoryState().commitTime * 1000)
                + "\n\tJVM:           " + System.getProperty("java.version")
                + "\n\tJDA:           " + JDAInfo.VERSION
                + "\n";
    }
}
