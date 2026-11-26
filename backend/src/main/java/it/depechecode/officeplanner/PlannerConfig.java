package it.depechecode.officeplanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.depechecode.officeplanner.ai.AiNarrator;
import it.depechecode.officeplanner.ai.LlmClient;
import it.depechecode.officeplanner.ai.NoopLlmClient;
import it.depechecode.officeplanner.ai.OpenAiCompatibleLlmClient;
import it.depechecode.officeplanner.engine.RecommendationEngine;
import it.depechecode.officeplanner.engine.ScoringWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Wiring: il dominio resta puro Java, Spring compone i pezzi qui. */
@Configuration
public class PlannerConfig {

    private static final Logger log = LoggerFactory.getLogger(PlannerConfig.class);

    @Bean
    public RecommendationEngine recommendationEngine(
            @Value("${planner.weights.required}") int required,
            @Value("${planner.weights.optional}") int optional,
            @Value("${planner.weights.team-member}") int teamMember,
            @Value("${planner.weights.same-skill-in-area}") int sameSkill,
            @Value("${planner.weights.mentorship}") int mentorship) {
        return new RecommendationEngine(new ScoringWeights(required, optional, teamMember, sameSkill, mentorship));
    }

    @Bean
    public LlmClient llmClient(@Value("${llm.base-url}") String baseUrl,
                               @Value("${llm.api-key:}") String apiKey,
                               @Value("${llm.model}") String model,
                               @Value("${llm.fallback-models:}") String fallbackModels,
                               ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Nessun token LLM configurato: uso i testi a template");
            return new NoopLlmClient();
        }
        List<String> models = Stream.concat(Stream.of(model), Arrays.stream(fallbackModels.split(",")))
                .map(String::trim)
                .filter(m -> !m.isEmpty())
                .distinct()
                .toList();
        log.info("LLM attivo: {} su {}", models, baseUrl);
        return new OpenAiCompatibleLlmClient(baseUrl, apiKey, models, mapper);
    }

    @Bean
    public AiNarrator aiNarrator(LlmClient llmClient, ObjectMapper mapper) {
        return new AiNarrator(llmClient, mapper);
    }
}
