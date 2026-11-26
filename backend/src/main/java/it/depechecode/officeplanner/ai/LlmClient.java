package it.depechecode.officeplanner.ai;

import java.util.Optional;

/**
 * Porta verso un LLM. Optional.empty() significa "LLM non disponibile":
 * chi chiama usa un testo a template, così la demo non si blocca mai.
 */
public interface LlmClient {

    Optional<String> complete(String systemPrompt, String userPrompt);

    String providerName();
}
