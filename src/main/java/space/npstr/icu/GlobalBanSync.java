/*
 * Copyright (C) 2017 - 2018 Dennis Neufeld
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

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.requests.RequestFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.icu.db.entities.GlobalBan;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.sqlsauce.DatabaseWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by napster on 11.03.18.
 */
public class GlobalBanSync {

    private static final Logger log = LoggerFactory.getLogger(GlobalBanSync.class);

    private final Supplier<DatabaseWrapper> wrapperSupp;
    private final Supplier<ShardManager> shardManagerSupp;

    public GlobalBanSync(Supplier<DatabaseWrapper> wrapperSupplier, Supplier<ShardManager> shardManagerSupplier) {
        this.wrapperSupp = wrapperSupplier;
        this.shardManagerSupp = shardManagerSupplier;


        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, GlobalBanSync.class.getSimpleName() + "-worker")
        );

        service.scheduleAtFixedRate(() -> {
            try {
                List<GlobalBan> globalBans = Collections.unmodifiableList(wrapperSupp.get().loadAll(GlobalBan.class));
                shardManagerSupp.get().getGuildCache().forEach(guild -> {
                    try {
                        syncGlobalBans(guild, globalBans);
                    } catch (Exception e) {
                        log.error("Failed to sync global bans for guild {}", guild, e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to sync global bans", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void syncGlobalBans(Guild guild, List<GlobalBan> globalBans)
            throws InterruptedException, ExecutionException, TimeoutException {

        GuildSettings settings = wrapperSupp.get().getOrCreate(GuildSettings.key(guild));
        if (!settings.areGlobalBansEnabled()) {
            return;
        }

        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            //try to report the issue
            Long reportingChannelId = settings.getReportingChannelId();
            if (reportingChannelId != null) {
                TextChannel reportingChannel = guild.getTextChannelById(reportingChannelId);
                if (reportingChannel != null) {
                    reportingChannel.sendMessage("I am missing the ban permission to perform a sync of global bans."
                            + " Please fix the permission or turn off global bans for this guild.").queue();
                }
            }
            return;
        }


        Set<Long> guildBanList = guild.getBanList().submit().get(30, TimeUnit.SECONDS)
                .stream()
                .map(Guild.Ban::getUser)
                .map(ISnowflake::getIdLong)
                .collect(Collectors.toSet());

        List<GlobalBan> toBan = new ArrayList<>();

        for (GlobalBan ban : globalBans) {
            if (!guildBanList.contains(ban.getUserId())) {
                toBan.add(ban);
            }
        }


        List<RequestFuture> futures = new ArrayList<>();
        for (GlobalBan ban : toBan) {
            String reason = "[i.c.u. Global Ban List] " + ban.getReason();
            if (reason.length() >= 512) { //max audit log size
                reason = reason.substring(0, 512);
            }

            futures.add(guild.getController().ban(Long.toString(ban.getUserId()), 1, reason).submit());
        }

        //wait to be done before moving on
        RequestFuture.allOf(futures);
    }
}
