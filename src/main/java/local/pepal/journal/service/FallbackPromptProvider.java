package local.pepal.journal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FallbackPromptProvider {

    private final ObjectMapper objectMapper;
    private final Map<String, List<PromptQuestionDto>> questionsByKind;

    public FallbackPromptProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.questionsByKind = loadQuestions();
    }

    private Map<String, List<PromptQuestionDto>> loadQuestions() {
        try (InputStream is = new ClassPathResource("prompts/fallback-questions.json").getInputStream()) {
            List<PromptQuestionDto> allQuestions = objectMapper.readValue(is, new TypeReference<List<PromptQuestionDto>>() {});
            return allQuestions.stream()
                    .collect(Collectors.groupingBy(q -> q.kind().toUpperCase()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load static fallback questions from prompts/fallback-questions.json", e);
        }
    }

    public List<PromptQuestionDto> getQuestionsForDate(LocalDate date) {
        return getQuestionsForDate(date, 0);
    }

    public List<PromptQuestionDto> getQuestionsForDate(LocalDate date, int variant) {
        long seed = date.toEpochDay() + variant;

        List<PromptQuestionDto> reflective = questionsByKind.getOrDefault("REFLECTIVE", List.of());
        List<PromptQuestionDto> playful = questionsByKind.getOrDefault("PLAYFUL", List.of());
        List<PromptQuestionDto> forward = questionsByKind.getOrDefault("FORWARD", List.of());

        if (reflective.isEmpty() || playful.isEmpty() || forward.isEmpty()) {
            throw new IllegalStateException("Fallback questions must contain REFLECTIVE, PLAYFUL, and FORWARD kinds");
        }

        int refIdx = (int) Math.floorMod(seed, reflective.size());
        int playIdx = (int) Math.floorMod(seed * 31 + 7, playful.size());
        int fwdIdx = (int) Math.floorMod(seed * 127 + 13, forward.size());

        List<PromptQuestionDto> selected = new ArrayList<>(3);
        selected.add(reflective.get(refIdx));
        selected.add(playful.get(playIdx));
        selected.add(forward.get(fwdIdx));
        return List.copyOf(selected);
    }
}
