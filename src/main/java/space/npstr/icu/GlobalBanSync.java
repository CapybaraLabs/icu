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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import space.npstr.icu.db.entities.GlobalBan;
import space.npstr.icu.db.entities.GlobalBanRepository;
import space.npstr.icu.db.entities.GuildSettings;
import space.npstr.icu.db.entities.GuildSettingsRepository;

/**
 * Created by napster on 11.03.18.
 */
@Component
public class GlobalBanSync {

    private static final Logger log = LoggerFactory.getLogger(GlobalBanSync.class);

    private final GuildSettingsRepository guildSettingsRepo;
    private final ShardManager shardManager;

    public GlobalBanSync(GlobalBanRepository globalBanRepo, GuildSettingsRepository guildSettingsRepo, ShardManager shardManager) {
        this.guildSettingsRepo = guildSettingsRepo;
        this.shardManager = shardManager;


        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, GlobalBanSync.class.getSimpleName() + "-worker")
        );

        service.scheduleAtFixedRate(() -> {
            try {
                List<GlobalBan> globalBans = Collections.unmodifiableList(globalBanRepo.findAll());
                this.shardManager.getGuildCache().forEach(guild -> {
                    try {
                        syncGlobalBans(guild, globalBans);
                    } catch (Exception e) {
                        log.error("Failed to sync global bans for guild {}", guild, e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to sync global bans", e);
            }
        }, 1, 10, TimeUnit.MINUTES);
    }

    private void syncGlobalBans(Guild guild, List<GlobalBan> globalBans)
            throws InterruptedException, ExecutionException, TimeoutException {

        GuildSettings settings = guildSettingsRepo.findOrCreateByGuild(guild);
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


        Set<Long> guildBanList = guild.retrieveBanList().submit().get(5, TimeUnit.MINUTES)
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


        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (GlobalBan ban : toBan) {
            String reason = "[i.c.u. Global Ban List] " + ban.getReason();
            if (reason.length() >= 512) { //max audit log size
                reason = reason.substring(0, 512);
            }

            futures.add(guild.ban(UserSnowflake.fromId(ban.getUserId()), 1, TimeUnit.DAYS).reason(reason).submit());
        }

        //wait to be done before moving on
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
    }
}
