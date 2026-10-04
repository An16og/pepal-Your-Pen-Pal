package local.pepal.journal.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@ConditionalOnMissingBean(value = LifeSnapshotProvider.class, ignored = NoOpLifeSnapshotProvider.class)
public class NoOpLifeSnapshotProvider implements LifeSnapshotProvider {

    @Override
    public Optional<String> latestSnapshot(LocalDate asOf) {
        return Optional.empty();
    }
}
