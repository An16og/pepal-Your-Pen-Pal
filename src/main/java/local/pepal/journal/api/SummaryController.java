package local.pepal.journal.api;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SummaryResponse;
import local.pepal.journal.service.SummaryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/summaries")
public class SummaryController {
    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    public List<SummaryResponse> list(
            @RequestParam(defaultValue = "WEEK") String type,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return summaryService.getSummaries(type, limit, offset);
    }

    @GetMapping("/{id}")
    public SummaryResponse get(@PathVariable long id) {
        return summaryService.getSummaryById(id);
    }

    @GetMapping("/{id}/entries")
    public List<EntryResponse> entries(@PathVariable long id) {
        return summaryService.getSummaryEntries(id);
    }

    @PostMapping("/generate")
    public ResponseEntity<SummaryResponse> generate(
            @RequestParam(defaultValue = "WEEK") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        SummaryResponse summary = summaryService.generateSummary(type, date, force);
        return ResponseEntity.ok(summary);
    }
}
