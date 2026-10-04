package local.pepal.journal.api;

import local.pepal.journal.api.JournalDtos.DailyPromptResponse;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import local.pepal.journal.api.JournalExceptions.ReshuffleLimitReachedException;
import local.pepal.journal.service.DailyPromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DailyPromptControllerTest {

    @Mock
    private DailyPromptService dailyPromptService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DailyPromptController controller = new DailyPromptController(dailyPromptService);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void getTodayPrompt_returnsOk() throws Exception {
        LocalDate today = LocalDate.now();
        List<PromptQuestionDto> questions = List.of(
                new PromptQuestionDto("What felt steadying today?", "REFLECTIVE"),
                new PromptQuestionDto("If today was a fruit, which one?", "PLAYFUL"),
                new PromptQuestionDto("What is one thing for tomorrow?", "FORWARD")
        );

        when(dailyPromptService.getOrGenerate(today)).thenReturn(new DailyPromptResponse(
                today, questions, "GENERATED", 1
        ));

        mockMvc.perform(get("/api/prompts/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.source").value("GENERATED"))
                .andExpect(jsonPath("$.reshufflesRemaining").value(1))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].kind").value("REFLECTIVE"));
    }

    @Test
    void getTodayPrompt_futureDateBeyondTomorrow_returns400ProblemDetail() throws Exception {
        LocalDate futureDate = LocalDate.now().plusDays(5);

        mockMvc.perform(get("/api/prompts/today?date=" + futureDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid date"))
                .andExpect(jsonPath("$.detail").value("Date cannot be more than 1 day in the future: " + futureDate));
    }

    @Test
    void reshufflePrompt_returnsOk() throws Exception {
        LocalDate today = LocalDate.now();
        List<PromptQuestionDto> questions = List.of(
                new PromptQuestionDto("New reflective question?", "REFLECTIVE"),
                new PromptQuestionDto("New playful question?", "PLAYFUL"),
                new PromptQuestionDto("New forward question?", "FORWARD")
        );

        when(dailyPromptService.reshuffle(today)).thenReturn(new DailyPromptResponse(
                today, questions, "GENERATED", 0
        ));

        mockMvc.perform(post("/api/prompts/reshuffle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GENERATED"))
                .andExpect(jsonPath("$.reshufflesRemaining").value(0))
                .andExpect(jsonPath("$.questions[0].text").value("New reflective question?"));
    }

    @Test
    void reshufflePrompt_whenLimitReached_returns409ConflictProblemDetail() throws Exception {
        LocalDate today = LocalDate.now();

        when(dailyPromptService.reshuffle(today))
                .thenThrow(new ReshuffleLimitReachedException("Daily prompts can only be reshuffled once per day."));

        mockMvc.perform(post("/api/prompts/reshuffle"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reshuffle limit reached"))
                .andExpect(jsonPath("$.detail").value("Daily prompts can only be reshuffled once per day."));
    }
}
