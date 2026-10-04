package local.pepal.journal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class LegacyConfigDeprecationWarningListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LegacyConfigDeprecationWarningListener.class);

    private static final Set<String> KNOWN_LEGACY_KEYS = Set.of(
            "usher.trash.retention-days",
            "usher.trash.purge-cron",
            "usher.prompts.max-context-chars",
            "usher.prompts.generation-timeout-seconds",
            "usher.prompts.similarity-threshold",
            "usher.prompts.temperature",
            "usher.prompts.pregenerate-cron"
    );

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        checkAndWarn(event.getApplicationContext().getEnvironment());
    }

    public static Set<String> findLegacyKeys(Environment env) {
        Set<String> found = new LinkedHashSet<>();
        if (env instanceof ConfigurableEnvironment confEnv) {
            for (PropertySource<?> ps : confEnv.getPropertySources()) {
                if (ps instanceof EnumerablePropertySource<?> eps) {
                    for (String name : eps.getPropertyNames()) {
                        if (name.startsWith("usher.")) {
                            found.add(name);
                        }
                    }
                }
            }
        }
        for (String known : KNOWN_LEGACY_KEYS) {
            if (env.containsProperty(known)) {
                found.add(known);
            }
        }
        return found;
    }

    public static void checkAndWarn(Environment env) {
        Set<String> legacyKeys = findLegacyKeys(env);
        for (String key : legacyKeys) {
            String replacement = key.replaceFirst("^usher\\.", "otto.");
            log.warn("Found deprecated configuration property '{}'. Please migrate to '{}'. Support for 'usher.*' keys will be removed in a future release.",
                    key, replacement);
        }
    }
}
