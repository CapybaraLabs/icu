package space.npstr.icu.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discord")
public record DiscordProperties(
	String token
) {}
