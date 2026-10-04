package local.pepal.journal.api;

public final class JournalExceptions {
    private JournalExceptions() {}

    public static class EntryNotFoundException extends RuntimeException {
        public EntryNotFoundException(String message) {
            super(message);
        }
    }

    public static class RestoreConflictException extends RuntimeException {
        private final long conflictingEntryId;

        public RestoreConflictException(long conflictingEntryId, String message) {
            super(message);
            this.conflictingEntryId = conflictingEntryId;
        }

        public long getConflictingEntryId() {
            return conflictingEntryId;
        }
    }

    public static class ReshuffleLimitReachedException extends RuntimeException {
        public ReshuffleLimitReachedException(String message) {
            super(message);
        }
    }

    public static class InvalidDateException extends RuntimeException {
        public InvalidDateException(String message) {
            super(message);
        }
    }
}
