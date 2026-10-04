package local.pepal.journal.service;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.PromptPreviewResponse;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.api.JournalDtos.SettingsResponse;
import local.pepal.journal.api.JournalDtos.UpdateSettingsRequest;
import local.pepal.journal.repository.SettingsRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class SettingsService {
    private final SettingsRepository repository;
    private final PersonaPromptBuilder promptBuilder;
    private volatile SettingsRecord cachedSettings;

    private static final Set<String> ALLOWED_PRESETS = Set.of(
            PersonaPromptBuilder.PRESET_GENTLE,
            PersonaPromptBuilder.PRESET_PLAYFUL,
            PersonaPromptBuilder.PRESET_BLUNT,
            PersonaPromptBuilder.PRESET_COACH,
            PersonaPromptBuilder.PRESET_CUSTOM
    );

    private static final Set<String> ALLOWED_LANGUAGES = Set.of(
            PersonaPromptBuilder.LANG_EN,
            PersonaPromptBuilder.LANG_HINGLISH
    );

    public SettingsService(SettingsRepository repository, PersonaPromptBuilder promptBuilder) {
        this.repository = repository;
        this.promptBuilder = promptBuilder;
    }

    public SettingsRecord getSettings() {
        if (cachedSettings == null) {
            synchronized (this) {
                if (cachedSettings == null) {
                    cachedSettings = repository.get().orElseGet(() ->
                            new SettingsRecord((short) 1, PersonaPromptBuilder.PRESET_GENTLE, null, PersonaPromptBuilder.LANG_EN, null, OffsetDateTime.now())
                    );
                }
            }
        }
        return cachedSettings;
    }

    public SettingsResponse getSettingsResponse() {
        SettingsRecord s = getSettings();
        return toResponse(s);
    }

    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Settings request cannot be null");
        }

        String preset = request.personaPreset() != null ? request.personaPreset().trim().toUpperCase() : "";
        if (!ALLOWED_PRESETS.contains(preset)) {
            throw new IllegalArgumentException("Unknown persona preset: " + request.personaPreset() +
                    ". Allowed presets: " + ALLOWED_PRESETS);
        }

        String language = request.language() != null ? request.language().trim().toUpperCase() : "";
        if (!ALLOWED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Unknown language: " + request.language() +
                    ". Allowed languages: " + ALLOWED_LANGUAGES);
        }

        SettingsRecord existing = getSettings();
        String customPersonaToPersist;

        if (PersonaPromptBuilder.PRESET_CUSTOM.equals(preset)) {
            if (request.customPersona() == null || request.customPersona().isBlank()) {
                throw new IllegalArgumentException("customPersona is required and cannot be blank when preset is CUSTOM");
            }
            if (request.customPersona().length() > 500) {
                throw new IllegalArgumentException("customPersona cannot exceed 500 characters");
            }
            // Validate that sanitized custom persona is valid
            customPersonaToPersist = promptBuilder.sanitizeCustomPersona(request.customPersona());
        } else {
            // Ignored when preset is not CUSTOM, but retained if previously set or passed
            if (request.customPersona() != null && !request.customPersona().isBlank()) {
                if (request.customPersona().length() > 500) {
                    throw new IllegalArgumentException("customPersona cannot exceed 500 characters");
                }
                customPersonaToPersist = request.customPersona().trim();
            } else {
                customPersonaToPersist = existing.customPersona();
            }
        }

        String userNameToPersist = request.userName() != null && !request.userName().isBlank()
                ? request.userName().trim()
                : null;

        SettingsRecord updated = repository.update(preset, customPersonaToPersist, language, userNameToPersist);
        this.cachedSettings = updated;
        return toResponse(updated);
    }

    public PromptPreviewResponse previewPrompt(String presetParam, String customParam, String languageParam) {
        SettingsRecord current = getSettings();

        String preset = (presetParam != null && !presetParam.isBlank())
                ? presetParam.trim().toUpperCase()
                : current.personaPreset();

        if (!ALLOWED_PRESETS.contains(preset)) {
            throw new IllegalArgumentException("Unknown persona preset: " + presetParam);
        }

        String language = (languageParam != null && !languageParam.isBlank())
                ? languageParam.trim().toUpperCase()
                : current.language();

        if (!ALLOWED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Unknown language: " + languageParam);
        }

        String custom = customParam != null ? customParam : current.customPersona();
        if (PersonaPromptBuilder.PRESET_CUSTOM.equals(preset)) {
            if (custom == null || custom.isBlank()) {
                custom = "Reflect deeply with kind observations.";
            }
        }

        String prompt = promptBuilder.build(preset, custom, language, TaskType.CHAT);
        return new PromptPreviewResponse(preset, language, prompt);
    }

    private static SettingsResponse toResponse(SettingsRecord s) {
        return new SettingsResponse(
                s.personaPreset(),
                s.customPersona(),
                s.language(),
                s.userName(),
                s.updatedAt(),
                PersonaPromptBuilder.PRESET_DESCRIPTIONS
        );
    }
}
