package space.npstr.icu.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.npstr.sqlsauce.DatabaseWrapper;

@Configuration
public class DatabaseConfig {

	@Bean
	public DatabaseWrapper databaseWrapper(DbManager dbManager) {
		return dbManager.getDefaultDbWrapper();
	}

}
