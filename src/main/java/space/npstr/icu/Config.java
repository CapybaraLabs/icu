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

package space.npstr.icu;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

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

    public final String sentryDsn;

    public Config() throws IOException {

        final File sneakyFile = new File("sneaky.yaml");
        final Yaml yaml = new Yaml();
        try (Reader reader = new InputStreamReader(new FileInputStream(sneakyFile), StandardCharsets.UTF_8)) {
            final Map<String, Object> sneaky = yaml.load(reader);
            //change nulls to empty strings
            sneaky.keySet().forEach((String key) -> sneaky.putIfAbsent(key, ""));

            this.sentryDsn = (String) sneaky.getOrDefault("sentryDsn", "");
            new SentryConfiguration(sentryDsn).init();
        }
    }
}

