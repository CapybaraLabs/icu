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

package space.npstr.icu.discord;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import space.npstr.icu.db.entities.GuildSettingsRepository;

@Service
public class AdminService {

	private final GuildSettingsRepository guildSettingsRepo;

	private final AsyncLoadingCache<Object, ApplicationInfo> applicationInfoCache;

	public AdminService(GuildSettingsRepository guildSettingsRepo, ObjectProvider<ShardManager> shardManager) {
		this.guildSettingsRepo = guildSettingsRepo;

		this.applicationInfoCache = Caffeine.newBuilder()
			.refreshAfterWrite(1, TimeUnit.HOURS)
			.buildAsync((__, ___) -> {
				JDA jda = shardManager.getObject().getShards().stream()
					.filter(it -> it.getStatus() == JDA.Status.CONNECTED)
					.findAny().orElseThrow();
				return jda.retrieveApplicationInfo().submit();
			});
	}

	public boolean isBotOwner(User user) {
		ApplicationInfo appInfo = applicationInfoCache.get("foo").join();
		return appInfo != null
			&& appInfo.getOwner().getIdLong() == user.getIdLong();
	}

	public boolean isAdmin(Member member) {
		return isBotOwner(member.getUser())
			|| member.isOwner()
			|| member.hasPermission(Permission.ADMINISTRATOR)
			|| guildSettingsRepo.findOrCreateByGuild(member.getGuild()).isAdmin(member);
	}
}
