package it.depechecode.officeplanner.ai;

import java.util.Optional;

/** Usato quando non è configurato nessun token: tutte le risposte arrivano dai template. */
public class NoopLlmClient implements LlmClient {

    @Override
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        return Optional.empty();
    }

    @Override
    public String providerName() {
        return "template";
    }
}
