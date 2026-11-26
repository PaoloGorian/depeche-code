package it.depechecode.officeplanner.ai;

import java.util.List;
import java.util.Optional;

/**
 * Porta verso un LLM. Optional.empty() significa "LLM non disponibile":
 * chi chiama usa un testo a template, così la demo non si blocca mai.
 */
public interface LlmClient {

    /** Un turno di conversazione: role = "user" oppure "assistant". */
    record Turn(String role, String content) {
    }

    /** Risposta del modello, con il nome del modello che ha risposto davvero (può essere un fallback). */
    record Reply(String text, String model) {
    }

    /**
     * Conversazione completa.
     * @param json true per chiedere al modello una risposta in JSON
     */
    Optional<Reply> chat(String systemPrompt, List<Turn> turns, boolean json);

    default Optional<Reply> complete(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, List.of(new Turn("user", userPrompt)), false);
    }

    String providerName();
}
