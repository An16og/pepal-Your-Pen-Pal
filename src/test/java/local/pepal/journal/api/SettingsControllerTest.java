package local.pepal.journal.api;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.api.JournalDtos.PromptPreviewResponse;
import local.pepal.journal.api.JournalDtos.SettingsResponse;
import local.pepal.journal.api.JournalDtos.UpdateSettingsRequest;
import local.pepal.journal.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SettingsController controller = new SettingsController(settingsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSettings_returnsCurrentSettingsAndPresets() throws Exception {
        when(settingsService.getSettingsResponse()).thenReturn(new SettingsResponse(
                "GENTLE",
                null,
                "EN",
                "Anujj",
                OffsetDateTime.now(),
                Map.of("GENTLE", "Warm and soft reflection.")
        ));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("GENTLE"))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.availablePresets.GENTLE").isNotEmpty());
    }

    @Test
    void updateSettings_updatesSuccessfully() throws Exception {
        when(settingsService.updateSettings(any(UpdateSettingsRequest.class))).thenReturn(new SettingsResponse(
                "COACH",
                null,
                "HINGLISH",
                null,
                OffsetDateTime.now(),
                Map.of("COACH", "Action-oriented reflection.")
        ));

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personaPreset": "COACH",
                                    "language": "HINGLISH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("COACH"))
                .andExpect(jsonPath("$.language").value("HINGLISH"));
    }

    @Test
    void updateSettings_whenValidationFails_returns400ProblemDetail() throws Exception {
        when(settingsService.updateSettings(any(UpdateSettingsRequest.class)))
                .thenThrow(new IllegalArgumentException("customPersona is required and cannot be blank when preset is CUSTOM"));

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personaPreset": "CUSTOM",
                                    "customPersona": "   ",
                                    "language": "EN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("customPersona is required and cannot be blank when preset is CUSTOM"));
    }

    @Test
    void previewPrompt_returnsComposedPrompt() throws Exception {
        when(settingsService.previewPrompt("PLAYFUL", null, "EN")).thenReturn(new PromptPreviewResponse(
                "PLAYFUL",
                "EN",
                "[PERSONA STYLE]\nPlayful\n\n[CRITICAL SAFETY & ETHICAL BOUNDARIES - STRICT PRECEDENCE]"
        ));

        mockMvc.perform(get("/api/settings/preview?preset=PLAYFUL&language=EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("PLAYFUL"))
                .andExpect(jsonPath("$.systemPrompt").isNotEmpty());
    }
}
