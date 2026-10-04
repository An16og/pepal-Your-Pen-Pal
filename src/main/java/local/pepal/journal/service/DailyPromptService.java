package local.pepal.journal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.DailyPromptRecord;
import local.pepal.journal.api.JournalDtos.DailyPromptResponse;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.api.JournalExceptions.ReshuffleLimitReachedException;
import local.pepal.journal.repository.DailyPromptRepository;
import local.pepal.journal.service.PromptContextAssembler.PromptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class DailyPromptService {

    private static final Logger log = LoggerFactory.getLogger(DailyPromptService.class);
    private static final Set<String> ALLOWED_KINDS = Set.of("REFLECTIVE", "PLAYFUL", "FORWARD");

    private final DailyPromptRepository dailyPromptRepository;
    private final PromptContextAssembler contextAssembler;
    private final FallbackPromptProvider fallbackPromptProvider;
    private final SettingsService settingsService;
    private final PersonaPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private final String chatModelName;
    private final double temperature;
    private final int timeoutSeconds;
    private final double similarityThreshold;

    private final ConcurrentHashMap<LocalDate, Object> dateLocks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DailyPromptService(
            DailyPromptRepository dailyPromptRepository,
            PromptContextAssembler contextAssembler,
            FallbackPromptProvider fallbackPromptProvider,
            SettingsService settingsService,
            PersonaPromptBuilder promptBuilder,
            ChatClient chatClient,
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.chat.options.model:qwen:4b}") String chatModelName,
            @Value("${otto.prompts.temperature:${usher.prompts.temperature:0.8}}") double temperature,
            @Value("${otto.prompts.generation-timeout-seconds:${usher.prompts.generation-timeout-seconds:20}}") int timeoutSeconds,
            @Value("${otto.prompts.similarity-threshold:${usher.prompts.similarity-threshold:0.7}}") double similarityThreshold
    ) {
        this.dailyPromptRepository = dailyPromptRepository;
        this.contextAssembler = contextAssembler;
        this.fallbackPromptProvider = fallbackPromptProvider;
        this.settingsService = settingsService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.chatModelName = chatModelName;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.similarityThreshold = similarityThreshold;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public DailyPromptResponse getOrGenerate(LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();

        // Fast path: check DB
        Optional<DailyPromptRecord> existing = dailyPromptRepository.findForDate(date);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // Concurrency control per date
        Object lock = dateLocks.computeIfAbsent(date, d -> new Object());
        synchronized (lock) {
            try {
                // Double-check inside lock
                Optional<DailyPromptRecord> opt = dailyPromptRepository.findForDate(date);
                if (opt.isPresent()) {
                    return toResponse(opt.get());
                }

                PromptContext context = contextAssembler.assemble(date, null);

                // Cold start: if < 3 non-deleted entries before date, skip Ollama
                if (context.nonDeletedCount() < 3) {
                    List<PromptQuestionDto> fallbackQuestions = fallbackPromptProvider.getQuestionsForDate(date);
                    DailyPromptRecord record = new DailyPromptRecord(
                            date, fallbackQuestions, "FALLBACK", (short) 0, OffsetDateTime.now());
                    dailyPromptRepository.insert(record);
                    return toResponse(record);
                }

                // Attempt generation with fallback
                DailyPromptRecord record = generateWithFallback(date, context, (short) 0);
                dailyPromptRepository.insert(record);
                return toResponse(record);
            } finally {
                dateLocks.remove(date, lock);
            }
        }
    }

    public DailyPromptResponse reshuffle(LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();

        Object lock = dateLocks.computeIfAbsent(date, d -> new Object());
        synchronized (lock) {
            try {
                DailyPromptRecord current = dailyPromptRepository.findForDate(date)
                        .orElseGet(() -> {
                            getOrGenerate(date);
                            return dailyPromptRepository.findForDate(date)
                                    .orElseThrow(() -> new IllegalStateException("Failed to find or generate prompt for " + date));
                        });

                boolean isCurrentFallback = "FALLBACK".equalsIgnoreCase(current.source());
                if (!isCurrentFallback && current.reshuffleCount() >= 1) {
                    throw new ReshuffleLimitReachedException("Daily prompts can only be reshuffled once per day.");
                }

                short nextReshuffleCount = isCurrentFallback ? current.reshuffleCount() : (short) (current.reshuffleCount() + 1);

                PromptContext context = contextAssembler.assemble(date, current.questions());

                if (context.nonDeletedCount() < 3) {
                    List<PromptQuestionDto> fallbackQuestions = fallbackPromptProvider.getQuestionsForDate(date, 1);
                    dailyPromptRepository.updateQuestionsAndSource(date, fallbackQuestions, "FALLBACK", nextReshuffleCount);
                    DailyPromptRecord updated = new DailyPromptRecord(
                            date, fallbackQuestions, "FALLBACK", nextReshuffleCount, current.createdAt());
                    return toResponse(updated);
                }

                DailyPromptRecord generated = generateWithFallback(date, context, nextReshuffleCount);
                dailyPromptRepository.updateQuestionsAndSource(
                        date, generated.questions(), generated.source(), nextReshuffleCount);
                DailyPromptRecord updated = new DailyPromptRecord(
                        date, generated.questions(), generated.source(), nextReshuffleCount, current.createdAt());
                return toResponse(updated);
            } finally {
                dateLocks.remove(date, lock);
            }
        }
    }

    @Scheduled(cron = "${otto.prompts.pregenerate-cron:${usher.prompts.pregenerate-cron:0 0 5 * * ?}}")
    public void pregenerateTodayPrompt() {
        try {
            LocalDate today = LocalDate.now();
            log.info("Running scheduled pregeneration of daily prompt for {}", today);
            getOrGenerate(today);
        } catch (Exception e) {
            log.warn("Scheduled pregeneration of daily prompt failed or was skipped: {}", e.getMessage());
        }
    }

    private DailyPromptRecord generateWithFallback(LocalDate date, PromptContext context, short reshuffleCount) {
        SettingsRecord settings = settingsService.getSettings();
        String systemPrompt = promptBuilder.build(settings, TaskType.DAILY_PROMPT);
        String userPrompt = buildUserMessage(context);

        // Attempt 1: primary temperature
        try {
            List<PromptQuestionDto> questions = attemptGeneration(systemPrompt, userPrompt, this.temperature, context.avoidQuestions());
            if (questions != null) {
                return new DailyPromptRecord(date, questions, "GENERATED", reshuffleCount, OffsetDateTime.now());
            }
        } catch (Exception e) {
            log.warn("Prompt generation attempt 1 failed: {}", e.getMessage());
        }

        // Attempt 2: retry once with reduced temperature 0.5
        try {
            log.info("Retrying prompt generation with temperature 0.5 for date {}", date);
            List<PromptQuestionDto> questions = attemptGeneration(systemPrompt, userPrompt, 0.5, context.avoidQuestions());
            if (questions != null) {
                return new DailyPromptRecord(date, questions, "GENERATED", reshuffleCount, OffsetDateTime.now());
            }
        } catch (Exception e) {
            log.warn("Prompt generation retry attempt failed: {}", e.getMessage());
        }

        // Fallback to static questions
        log.warn("Prompt generation failed or timed out. Falling back to deterministic static questions for {}", date);
        List<PromptQuestionDto> fallback = fallbackPromptProvider.getQuestionsForDate(date);
        return new DailyPromptRecord(date, fallback, "FALLBACK", reshuffleCount, OffsetDateTime.now());
    }

    private List<PromptQuestionDto> attemptGeneration(
            String systemPrompt, String userMessage, double temp, List<PromptQuestionDto> avoidQuestions) throws Exception {

        Callable<String> task = () -> chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(OllamaOptions.builder()
                        .model(chatModelName)
                        .format("json")
                        .temperature(temp)
                        .build())
                .call()
                .content();

        Future<String> future = executor.submit(task);
        String rawOutput;
        try {
            rawOutput = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Timed out waiting for Ollama prompt generation after " + timeoutSeconds + "s");
        }

        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalStateException("Ollama returned empty response for prompt generation");
        }

        String cleanedJson = stripMarkdownFences(rawOutput);
        List<PromptQuestionDto> parsed = objectMapper.readValue(cleanedJson, new TypeReference<List<PromptQuestionDto>>() {});

        return validateAndNormalizeQuestions(parsed, avoidQuestions);
    }

    public List<PromptQuestionDto> validateAndNormalizeQuestions(
            List<PromptQuestionDto> rawQuestions, List<PromptQuestionDto> avoidQuestions) {

        if (rawQuestions == null || rawQuestions.size() != 3) {
            throw new IllegalArgumentException("Expected exactly 3 questions, got: " + (rawQuestions != null ? rawQuestions.size() : 0));
        }

        Map<String, PromptQuestionDto> byKind = new LinkedHashMap<>();
        for (PromptQuestionDto q : rawQuestions) {
            if (q.kind() == null || q.text() == null) {
                throw new IllegalArgumentException("Question text and kind must not be null");
            }
            String upperKind = q.kind().strip().toUpperCase();
            if (!ALLOWED_KINDS.contains(upperKind)) {
                throw new IllegalArgumentException("Disallowed question kind: " + upperKind);
            }
            if (byKind.containsKey(upperKind)) {
                throw new IllegalArgumentException("Duplicate question kind: " + upperKind);
            }

            String text = q.text().strip();
            if (text.isEmpty() || text.length() > 160) {
                throw new IllegalArgumentException("Question text must be 1 to 160 characters, was: " + text.length());
            }
            if (!text.endsWith("?")) {
                throw new IllegalArgumentException("Question text must end with '?'");
            }
            if (!isSingleSentence(text)) {
                throw new IllegalArgumentException("Question must be exactly one sentence: " + text);
            }
            if (avoidQuestions != null && hasHighOverlap(text, avoidQuestions)) {
                throw new IllegalArgumentException("Question has high token overlap with recently asked questions: " + text);
            }

            byKind.put(upperKind, new PromptQuestionDto(text, upperKind));
        }

        if (!byKind.keySet().containsAll(ALLOWED_KINDS)) {
            throw new IllegalArgumentException("Must contain exactly 1 REFLECTIVE, 1 PLAYFUL, and 1 FORWARD question");
        }

        return List.of(
                byKind.get("REFLECTIVE"),
                byKind.get("PLAYFUL"),
                byKind.get("FORWARD")
        );
    }

    private boolean isSingleSentence(String text) {
        String body = text.substring(0, text.length() - 1).trim();
        // Check if there are sentence terminators like '. ', '? ', '! ' followed by a capital letter
        return !body.matches(".*[.!?]\\s+[A-Z].*");
    }

    private boolean hasHighOverlap(String text, List<PromptQuestionDto> avoidQuestions) {
        Set<String> tokens1 = tokenize(text);
        if (tokens1.isEmpty()) return false;

        for (PromptQuestionDto avoid : avoidQuestions) {
            Set<String> tokens2 = tokenize(avoid.text());
            if (tokens2.isEmpty()) continue;

            Set<String> intersection = new HashSet<>(tokens1);
            intersection.retainAll(tokens2);

            Set<String> union = new HashSet<>(tokens1);
            union.addAll(tokens2);

            double jaccard = (double) intersection.size() / union.size();
            if (jaccard > similarityThreshold) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokenize(String s) {
        if (s == null) return Set.of();
        return Arrays.stream(s.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private String stripMarkdownFences(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    private String buildUserMessage(PromptContext context) {
        return """
                %s

                Generate the 3 reflection questions for this person today following the required schema:
                [
                  {"text": "...", "kind": "REFLECTIVE"},
                  {"text": "...", "kind": "PLAYFUL"},
                  {"text": "...", "kind": "FORWARD"}
                ]
                Return ONLY the JSON array.
                """.formatted(context.renderedContext()).strip();
    }

    public DailyPromptResponse toResponse(DailyPromptRecord record) {
        int remaining = "FALLBACK".equalsIgnoreCase(record.source()) ? 1 : Math.max(0, 1 - record.reshuffleCount());
        return new DailyPromptResponse(
                record.promptDate(),
                record.questions(),
                record.source(),
                remaining
        );
    }
}
