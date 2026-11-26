package it.depechecode.officeplanner.ai;

import java.util.List;
import java.util.Optional;

/** Usato quando non è configurato nessun token: tutte le risposte arrivano dai template. */
public class NoopLlmClient implements LlmClient {

    @Override
    public Optional<Reply> chat(String systemPrompt, List<Turn> turns, boolean json) {
        return Optional.empty();
    }

    @Override
    public String providerName() {
        return "template";
    }
}
