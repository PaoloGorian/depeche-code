package it.depechecode.officeplanner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client per qualunque endpoint compatibile con OpenAI /chat/completions:
 * Google Gemini, GitHub Models, OpenAI, Azure OpenAI, Ollama, gateway aziendali.
 * Si cambia provider solo da configurazione (base-url, api-key, model).
 *
 * Se il modello principale è sovraccarico o non disponibile (429, 503, 404) prova i modelli di fallback, in ordine.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final Set<Integer> TRY_NEXT_MODEL = Set.of(404, 429, 500, 502, 503, 504);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** Tempo massimo complessivo per una risposta, fallback compresi: la chat non resta mai appesa. */
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(35);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    /** Modelli che hanno appena fallito: vengono provati per ultimi fino alla scadenza. */
    private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final List<String> models;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, List<String> models, ObjectMapper mapper) {
        if (models.isEmpty()) {
            throw new IllegalArgumentException("Serve almeno un modello LLM");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.models = List.copyOf(models);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Override
    public Optional<Reply> chat(String systemPrompt, List<Turn> turns, boolean json) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        turns.forEach(t -> messages.add(Map.of("role", t.role(), "content", t.content())));

        Instant deadline = Instant.now().plus(TOTAL_BUDGET);
        for (String model : modelsInOrder()) {
            Duration left = Duration.between(Instant.now(), deadline);
            if (left.toSeconds() < 3) {
                log.warn("LLM: tempo esaurito, uso il template");
                break;
            }
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("temperature", json ? 0.1 : 0.6);
                // margine ampio: i modelli "thinking" usano parte dei token per ragionare
                body.put("max_tokens", 4096);
                body.put("messages", messages);
                if (json) {
                    body.put("response_format", Map.of("type", "json_object"));
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                        .timeout(left.compareTo(REQUEST_TIMEOUT) < 0 ? left : REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 != 2) {
                    log.warn("LLM {} HTTP {}: {}", model, status, abbreviate(response.body()));
                    if (TRY_NEXT_MODEL.contains(status)) {
                        coolDown(model);
                        continue;
                    }
                    return Optional.empty();
                }
                JsonNode content = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
                if (content.isTextual() && !content.asText().isBlank()) {
                    cooldownUntil.remove(model);
                    return Optional.of(new Reply(content.asText().trim(), model));
                }
                log.warn("LLM {}: risposta vuota", model);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                log.warn("LLM {} non raggiungibile: {}", model, e.getMessage());
                coolDown(model);
            }
        }
        return Optional.empty();
    }

    /**
     * Prima i modelli "sani", nell'ordine configurato; poi quelli in pausa (come ultima spiaggia).
     * Così, se il principale è sovraccarico, le richieste successive non aspettano il suo timeout.
     */
    private List<String> modelsInOrder() {
        Instant now = Instant.now();
        List<String> healthy = new ArrayList<>();
        List<String> cooling = new ArrayList<>();
        for (String m : models) {
            Instant until = cooldownUntil.get(m);
            (until != null && until.isAfter(now) ? cooling : healthy).add(m);
        }
        healthy.addAll(cooling);
        return healthy;
    }

    private void coolDown(String model) {
        if (models.size() > 1) {
            cooldownUntil.put(model, Instant.now().plus(COOLDOWN));
        }
    }

    @Override
    public String providerName() {
        return models.get(0);
    }

    private static String abbreviate(String s) {
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }
}
