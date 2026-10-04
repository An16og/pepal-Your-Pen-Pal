package local.pepal.journal.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Seam for providing the latest life snapshot/profile summary for prompt context assembly.
 */
public interface LifeSnapshotProvider {
    Optional<String> latestSnapshot(LocalDate asOf);
}
