package local.pepal.journal.api;

import jakarta.validation.Valid;
import local.pepal.journal.api.JournalDtos.PromptPreviewResponse;
import local.pepal.journal.api.JournalDtos.SettingsResponse;
import local.pepal.journal.api.JournalDtos.UpdateSettingsRequest;
import local.pepal.journal.service.SettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingsService.getSettingsResponse();
    }

    @PutMapping
    public SettingsResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.updateSettings(request);
    }

    @GetMapping("/preview")
    public PromptPreviewResponse previewPrompt(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String customPersona,
            @RequestParam(required = false) String language
    ) {
        return settingsService.previewPrompt(preset, customPersona, language);
    }
}
