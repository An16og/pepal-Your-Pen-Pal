package local.pepal.journal.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConfigDeprecationWarningTest {

    @Test
    void detectsLegacyUsherKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("usher.trash.retention-days", "45");
        env.setProperty("usher.prompts.temperature", "0.9");
        env.setProperty("otto.trash.purge-cron", "0 0 3 * * ?");

        Set<String> legacyKeys = LegacyConfigDeprecationWarningListener.findLegacyKeys(env);

        assertTrue(legacyKeys.contains("usher.trash.retention-days"));
        assertTrue(legacyKeys.contains("usher.prompts.temperature"));
        assertFalse(legacyKeys.contains("otto.trash.purge-cron"));
    }

    @Test
    void returnsEmptyWhenNoLegacyKeysPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("otto.trash.retention-days", "30");
        env.setProperty("otto.prompts.temperature", "0.8");

        Set<String> legacyKeys = LegacyConfigDeprecationWarningListener.findLegacyKeys(env);

        assertTrue(legacyKeys.isEmpty());
    }
}
