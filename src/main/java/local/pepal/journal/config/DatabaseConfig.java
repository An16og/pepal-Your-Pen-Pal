package local.pepal.journal.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fallback configuration ensuring existing local installations using database 'usher'
 * seamlessly continue working when starting against their existing database.
 */
@Configuration
public class DatabaseConfig implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HikariDataSource ds) {
            String jdbcUrl = ds.getJdbcUrl();
            if (jdbcUrl != null && jdbcUrl.contains("/pepal")) {
                try (Connection ignored = ds.getConnection()) {
                    // Successfully connected to pepal database
                } catch (SQLException e) {
                    // Only attempt legacy fallback if database "pepal" does not exist (SQLState 3D000)
                    if ("3D000".equals(e.getSQLState())) {
                        String fallbackUrl = jdbcUrl.replace("/pepal", "/usher");
                        log.warn("Default database 'pepal' does not exist. Attempting legacy fallback to '{}' for existing local data.", fallbackUrl);
                        ds.setJdbcUrl(fallbackUrl);
                        if ("pepal".equals(ds.getUsername())) {
                            ds.setUsername("usher");
                        }
                    } else {
                        log.warn("Database connection check failed: {} (SQLState: {})", e.getMessage(), e.getSQLState());
                    }
                }
            }
        }
        return bean;
    }
}
