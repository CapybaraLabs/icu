package space.npstr.icu.info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
