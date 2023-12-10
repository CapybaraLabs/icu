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

package space.npstr.icu.info;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by napster on 30.09.17.
 */
public class AppInfo {

    public static AppInfo getAppInfo() {
        return AppInfoHolder.INSTANCE;
    }

    private static final Logger log = LoggerFactory.getLogger(AppInfo.class);

    //holder pattern
    private static final class AppInfoHolder {
        private static final AppInfo INSTANCE = new AppInfo();
    }

    public final String VERSION;
    public final String GROUP_ID;
    public final String ARTIFACT_ID;
    public final String BUILD_NUMBER;
    public final long BUILD_TIME;

    private AppInfo() {
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/app.properties");
        Properties prop = new Properties();
        try {
            prop.load(resourceAsStream);
        } catch (IOException e) {
            log.error("Failed to load app.properties", e);
        }
        this.VERSION = prop.getProperty("version");
        this.GROUP_ID = prop.getProperty("groupId");
        this.ARTIFACT_ID = prop.getProperty("artifactId");
        this.BUILD_NUMBER = prop.getProperty("buildNumber");
        this.BUILD_TIME = Long.parseLong(prop.getProperty("buildTime"));
    }

    public String getVersionBuild() {
        return this.VERSION + "_" + this.BUILD_NUMBER;
    }
}
