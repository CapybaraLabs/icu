package space.npstr.icu.discord;

import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.npstr.icu.ShardManagerManager;

@Configuration
public class DiscordConfig {

	@Bean
	public ShardManager shardManager(ShardManagerManager shardManagerManager) {
		return shardManagerManager.getShardManager();
	}
}
