package it.depechecode.officeplanner.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.depechecode.officeplanner.ai.LlmClient;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Usa l'LLM per trasformare una frase libera in un comando strutturato (JSON).
 * Valida sempre l'output: utente e data devono esistere, altrimenti scarta e lascia fare al parser a regole.
 */
public class LlmCommandParser {

    private static final String SYSTEM = """
            Sei il parser dei comandi di uno Smart Office Planner.
            Trasformi la frase dell'utente in UN oggetto JSON, senza testo prima o dopo e senza markdown.
            Schema: {"understood": true|false, "userId": "...", "date": "YYYY-MM-DD",
                     "officeNeed": "REQUIRED"|"OPTIONAL"|"FREE", "note": "descrizione breve dell'impegno o null"}
            REQUIRED = impegno che richiede la presenza in ufficio (default se si parla di un impegno).
            OPTIONAL = evento facoltativo, jolly. FREE = nessun impegno, impegno annullato o cancellato.
            Se la frase non parla di una persona, usa l'utente corrente. Se non indica il giorno, usa oggi.
            Se la frase non è un comando su impegni o disponibilità, rispondi {"understood": false}.
            """;

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public LlmCommandParser(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Optional<ChatCommand> parse(String message, String currentUserId, String today, OfficeData data) {
        String users = data.users().stream()
                .map(u -> u.id() + ": " + u.fullName() + " (team " + u.team() + ")")
                .collect(Collectors.joining("\n"));
        String week = data.week().stream()
                .map(d -> d + " = " + LocalDate.parse(d).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN))
                .collect(Collectors.joining(", "));
        String prompt = """
                Utenti:
                %s
                Utente corrente: %s
                Settimana: %s
                Oggi: %s

                Frase: %s
                """.formatted(users, currentUserId, week, today, message);

        return llm.complete(SYSTEM, prompt).flatMap(raw -> toCommand(raw, data));
    }

    private Optional<ChatCommand> toCommand(String raw, OfficeData data) {
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return Optional.empty();
            }
            JsonNode json = mapper.readTree(raw.substring(start, end + 1));
            if (!json.path("understood").asBoolean(false)) {
                return Optional.empty();
            }
            String userId = json.path("userId").asText("");
            String date = json.path("date").asText("");
            if (data.user(userId).isEmpty() || !data.week().contains(date)) {
                return Optional.empty();
            }
            OfficeNeed need = OfficeNeed.valueOf(json.path("officeNeed").asText("REQUIRED"));
            String note = json.path("note").isTextual() ? json.path("note").asText().trim() : null;
            return Optional.of(new ChatCommand(userId, date, need,
                    need == OfficeNeed.FREE || note == null || note.isEmpty() ? null : note, "llm"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
