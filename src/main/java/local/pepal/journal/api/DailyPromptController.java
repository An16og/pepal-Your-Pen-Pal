package local.pepal.journal.api;

import local.pepal.journal.api.JournalDtos.DailyPromptResponse;
import local.pepal.journal.api.JournalExceptions.InvalidDateException;
import local.pepal.journal.service.DailyPromptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/prompts")
public class DailyPromptController {

    private final DailyPromptService dailyPromptService;

    public DailyPromptController(DailyPromptService dailyPromptService) {
        this.dailyPromptService = dailyPromptService;
    }

    @GetMapping("/today")
    public DailyPromptResponse getTodayPrompt(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = validateDate(date);
        return dailyPromptService.getOrGenerate(targetDate);
    }

    @PostMapping("/reshuffle")
    public DailyPromptResponse reshufflePrompt(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = validateDate(date);
        return dailyPromptService.reshuffle(targetDate);
    }

    private LocalDate validateDate(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        LocalDate maxAllowed = LocalDate.now().plusDays(1);
        if (target.isAfter(maxAllowed)) {
            throw new InvalidDateException("Date cannot be more than 1 day in the future: " + target);
        }
        return target;
    }
}
