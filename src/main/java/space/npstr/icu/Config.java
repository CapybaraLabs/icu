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

import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import space.npstr.icu.info.GitRepoState;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

/**
 * Created by napster on 09.09.17.
 */
public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    public static final Config C;

    static {
        Config c;
        try {
            c = new Config();
        } catch (final IOException e) {
            c = null;
            log.error("Could not load config files!", e);
        }
        C = c;
    }

    public final String discordToken;
    public final String jdbcUrl;
    public final String sentryDsn;

    public Config() throws IOException {

        final File sneakyFile = new File("sneaky.yaml");
        final Yaml yaml = new Yaml();
        try (Reader reader = new InputStreamReader(new FileInputStream(sneakyFile), "UTF-8")) {
            final Map<String, Object> sneaky = yaml.load(reader);
            //change nulls to empty strings
            sneaky.keySet().forEach((String key) -> sneaky.putIfAbsent(key, ""));

            //sneaky stuff
            this.discordToken = (String) sneaky.getOrDefault("discordToken", "");

            String jdbc = (String) sneaky.getOrDefault("jdbcUrl", "");
            if (jdbc == null || jdbc.isEmpty()) {
                log.info("No jdbc url configured, using default docker one");
                jdbcUrl = "jdbc:postgresql://db:5432/icu?user=icu";
            } else {
                jdbcUrl = jdbc;
            }

            this.sentryDsn = (String) sneaky.getOrDefault("sentryDsn", "");
            if (this.sentryDsn != null && !this.sentryDsn.isEmpty()) {
                Sentry.init(this.sentryDsn).setRelease(GitRepoState.getGitRepositoryState().commitId);
            }
        }
    }
}

