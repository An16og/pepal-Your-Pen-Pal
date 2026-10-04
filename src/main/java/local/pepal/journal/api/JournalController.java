package local.pepal.journal.api;

import jakarta.validation.Valid;
import local.pepal.journal.api.JournalDtos.ChatRequest;
import local.pepal.journal.api.JournalDtos.ChatResponse;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SaveEntryRequest;
import local.pepal.journal.api.JournalDtos.TrashEntryResponse;
import local.pepal.journal.api.JournalDtos.UpdateEntryRequest;
import local.pepal.journal.service.JournalChatService;
import local.pepal.journal.service.JournalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class JournalController {
    private final JournalService journalService;
    private final JournalChatService chatService;

    public JournalController(JournalService journalService, JournalChatService chatService) {
        this.journalService = journalService;
        this.chatService = chatService;
    }

    @GetMapping("/entries")
    public List<EntryResponse> history() {
        return journalService.history();
    }

    @GetMapping("/entries/{id}")
    public EntryResponse getById(@PathVariable long id) {
        return journalService.get(id);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse save(@Valid @RequestBody SaveEntryRequest request) {
        return journalService.save(request);
    }

    @PutMapping("/entries/{id}")
    public EntryResponse update(@PathVariable long id, @Valid @RequestBody UpdateEntryRequest request) {
        return journalService.update(id, request);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable long id) {
        journalService.softDelete(id);
    }

    @GetMapping("/trash")
    public List<TrashEntryResponse> trash() {
        return journalService.trash();
    }

    @PostMapping("/entries/{id}/restore")
    public EntryResponse restore(@PathVariable long id) {
        return journalService.restore(id);
    }

    @DeleteMapping("/entries/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentDelete(@PathVariable long id) {
        journalService.permanentDelete(id);
    }

    @DeleteMapping("/trash")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void emptyTrash() {
        journalService.emptyTrash();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        if (request.history() == null || request.history().isEmpty()) {
            return chatService.ask(request.message());
        }
        return chatService.ask(request.message(), request.history());
    }
}
