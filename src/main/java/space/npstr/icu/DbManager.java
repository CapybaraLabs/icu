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

import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.info.AppInfo;
import space.npstr.sqlsauce.DatabaseConnection;
import space.npstr.sqlsauce.DatabaseWrapper;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.TimeUnit;

/**
 * Created by napster on 13.02.18.
 * <p>
 * Serves a lazily initialized database wrapper singletons
 */
@ThreadSafe
public class DbManager {

    private static final Logger log = LoggerFactory.getLogger(DbManager.class);

    @Nullable
    private volatile DatabaseWrapper defaultDbWrapper;
    private final Object defaultDbWrapperInitLock = new Object();

    public DatabaseWrapper getDefaultDbWrapper() {
        DatabaseWrapper singleton = defaultDbWrapper;
        if (singleton == null) {
            synchronized (defaultDbWrapperInitLock) {
                singleton = defaultDbWrapper;
                if (singleton == null) {
                    defaultDbWrapper = singleton = initDefaultDbWrapper();
                }
            }
        }
        return singleton;
    }

    public void shutdown() {
        synchronized (defaultDbWrapperInitLock) {
            DatabaseWrapper wrapper = this.defaultDbWrapper;
            if (wrapper != null) {
                wrapper.unwrap().shutdown();
            }
        }
    }

    private static DatabaseWrapper initDefaultDbWrapper() {
        try {
            DatabaseConnection databaseConnection = new DatabaseConnection.Builder("postgres", Config.C.jdbcUrl)
                    .setDialect("org.hibernate.dialect.PostgreSQL95Dialect")
                    .addEntityPackage("space.npstr.icu.db.entities")
                    .setAppName("icu_" + AppInfo.getAppInfo().getVersionBuild())
                    .setProxyDataSourceBuilder(new ProxyDataSourceBuilder()
                            .logSlowQueryBySlf4j(10, TimeUnit.SECONDS, SLF4JLogLevel.WARN, "SlowQueryLog")
                            .multiline()
                    )
                    .setHibernateProperty("hibernate.hbm2ddl.auto", "update")
                    .setHibernateProperty("hibernate.cache.use_second_level_cache", "true")
                    .setHibernateProperty("hibernate.cache.use_query_cache", "true")
                    .setHibernateProperty("net.sf.ehcache.configurationResourceName", "/ehcache.xml")
                    .setHibernateProperty("hibernate.cache.provider_configuration_file_resource_path", "ehcache.xml")
                    .setHibernateProperty("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
                    .build();
            return new DatabaseWrapper(databaseConnection);
        } catch (Exception e) {
            String message = "Failed to set up database";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
