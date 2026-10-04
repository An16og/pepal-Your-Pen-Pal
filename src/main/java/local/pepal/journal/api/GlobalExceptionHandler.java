package local.pepal.journal.api;

import local.pepal.journal.api.JournalExceptions.EntryNotFoundException;
import local.pepal.journal.api.JournalExceptions.RestoreConflictException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntryNotFoundException.class)
    public ProblemDetail handleNotFound(EntryNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(RestoreConflictException.class)
    public ProblemDetail handleRestoreConflict(RestoreConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("conflictingEntryId", ex.getConflictingEntryId());
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(JournalExceptions.ReshuffleLimitReachedException.class)
    public ProblemDetail handleReshuffleLimit(JournalExceptions.ReshuffleLimitReachedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Reshuffle limit reached");
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(JournalExceptions.InvalidDateException.class)
    public ProblemDetail handleInvalidDate(JournalExceptions.InvalidDateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid date");
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setProperty("error", ex.getMessage());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + (err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid input"))
                .findFirst()
                .orElse("Validation failed");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        pd.setProperty("error", msg);
        return pd;
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDatabaseUnavailable(DataAccessException ex) {
        String msg = "Local database is unavailable. Start PostgreSQL and try again.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, msg);
        pd.setProperty("error", msg);
        return pd;
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handleOllamaUnavailable(RestClientException ex) {
        String msg = "Local Ollama service is unavailable. Please ensure 'ollama serve' is running.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, msg);
        pd.setProperty("error", msg);
        return pd;
    }
}
