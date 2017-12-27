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
import net.dv8tion.jda.bot.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.entities.Game;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.info.AppInfo;
import space.npstr.icu.info.GitRepoState;
import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 27.12.17.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static DatabaseWrapper dbWrapper;
    private static ShardManager shardManager;

    public static void main(final String[] args) throws LoginException, InterruptedException {
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

        Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
        log.info(getVersionInfo());

        DatabaseConnection databaseConnection = null;
        int attempts = 0;
        while (databaseConnection == null && attempts++ < 10) {
            try {
                databaseConnection = createDbConn();
            } catch (Exception e) {
                log.info("Failed to set up database, retrying...", e);
                Thread.sleep(6000);
            }
        }
        if (databaseConnection == null) {
            log.error("Failed to set up database, exiting...");
            System.exit(1);
        }

        dbWrapper = new DatabaseWrapper(databaseConnection);


        DefaultShardManagerBuilder shardBuilder = new DefaultShardManagerBuilder()
                .setToken(Config.C.discordToken)
                .setGame(Game.watching("you"))
                .addEventListeners(new RoleChangesListener(dbWrapper))
                .setEnableShutdownHook(false)
                .setAudioEnabled(false);

        shardManager = shardBuilder.build();
    }

    private static DatabaseConnection createDbConn() {
        return new DatabaseConnection.Builder("postgres", Config.C.jdbcUrl)
                .setDialect("org.hibernate.dialect.PostgreSQL95Dialect")
                .addEntityPackage("space.npstr.icu.db.entities")
                .setAppName("icu_" + AppInfo.getAppInfo().getVersionBuild())
                .setProxyDataSourceBuilder(new ProxyDataSourceBuilder()
                        .logSlowQueryBySlf4j(10, TimeUnit.SECONDS, SLF4JLogLevel.WARN, "SlowQueryLog")
                        .multiline()
                )
                .build();
    }

    private static final Thread SHUTDOWN_HOOK = new Thread(() -> {
        log.info("Shutdown hook triggered!");
        //okHttpClient claims that a shutdown isn't necessary

        //shutdown JDA
        log.info("Shutting down shards");
        if (shardManager != null) shardManager.shutdown();

        //shutdown DB
        log.info("Shutting down database");
        dbWrapper.unwrap().shutdown();

        //shutdown logback logger
        log.info("Shutting down logger :rip:");
        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }, "shutdown-hook");

    public static final DateTimeFormatter TIME_IN_CENTRAL_EUROPE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z")
            .withZone(ZoneId.of("Europe/Berlin"));

    @Nonnull
    public static String asTimeInCentralEurope(final long epochMillis) {
        return TIME_IN_CENTRAL_EUROPE.format(Instant.ofEpochMilli(epochMillis));
    }

    @Nonnull
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
