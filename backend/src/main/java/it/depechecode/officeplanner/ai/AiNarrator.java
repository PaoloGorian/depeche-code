package it.depechecode.officeplanner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Trasforma l'output deterministico del motore in testo naturale tramite LLM.
 * Il motore decide, l'LLM spiega, conversa e capisce gli impegni scritti dall'utente:
 * così il risultato resta stabile e verificabile.
 */
public class AiNarrator {

    private static final Logger log = LoggerFactory.getLogger(AiNarrator.class);
    private static final int MAX_HISTORY = 16;

    private static final String SYSTEM = """
            Sei l'assistente di "Should I Stay or Should I Go?", lo Smart Office Planner del team Depeche Code.
            Aiuti i dipendenti a scegliere i giorni in ufficio per incontrare le persone giuste.
            Rispondi sempre in italiano, con tono amichevole e professionale, dando del tu.
            Non dedurre il genere dal nome: usa forme neutre (per esempio "ciao", non "benvenuto" o "benvenuta").
            Usa solo i fatti forniti, senza inventare persone, eventi o numeri.
            Il piano (GO/STAY e aree) lo calcola il motore: tu lo spieghi, non lo cambi di tua iniziativa.
            Non usare elenchi puntati, markdown o trattini lunghi.
            """;

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public AiNarrator(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- chat "Pianifica con l'AI"

    /** Primo messaggio della chat: il consiglio della settimana per l'utente loggato. */
    public AiText opening(User user, Recommendation rec, OfficeData data) {
        String prompt = context(user, rec, data) + """

                Scrivi il primo messaggio della chat (massimo 4 frasi): saluta per nome, consiglia quali giorni
                andare in ufficio e in quale area, spiegando il motivo principale e citando i colleghi chiave o la mentorship.
                Chiudi invitando a scriverti eventuali impegni della settimana per aggiornare il piano.
                """;
        return llm.complete(SYSTEM, prompt)
                .map(r -> new AiText(r.text(), "llm", r.model()))
                .orElseGet(() -> new AiText(templateOpening(user, rec, data), "template", "template"));
    }

    /**
     * Legge l'ultimo messaggio dell'utente e ne estrae gli impegni della settimana.
     * Restituisce lista vuota se non ce ne sono o se l'LLM non è disponibile.
     */
    public List<DayStatus> extractCommitments(User user, String message, OfficeData data) {
        String days = data.week().stream()
                .map(d -> dayLabel(d) + " = " + d)
                .collect(Collectors.joining(", "));
        String current = user.availability().isEmpty() ? "nessuno" : user.availability().stream()
                .map(s -> s.date() + " " + s.officeNeed() + (s.note() == null ? "" : " (" + s.note() + ")"))
                .collect(Collectors.joining("; "));
        String system = """
                Estrai dal messaggio dell'utente i suoi impegni per la settimana lavorativa e rispondi SOLO con JSON:
                {"commitments":[{"date":"YYYY-MM-DD","officeNeed":"REQUIRED|OPTIONAL|REMOTE|FREE","note":"descrizione breve"}]}
                REQUIRED = deve essere in ufficio (riunione in presenza, cliente in sede, evento obbligatorio).
                OPTIONAL = evento in ufficio utile ma non obbligatorio.
                REMOTE = non può venire in ufficio quel giorno (impegno da casa, permesso, ferie, trasferta, visita medica).
                FREE = un impegno che aveva indicato è stato annullato.
                Usa solo le date della settimana indicata. Includi SOLO gli impegni citati in questo messaggio:
                non ripetere gli impegni già noti se il messaggio non li modifica.
                Se il messaggio non contiene impegni restituisci {"commitments":[]}.
                """;
        String prompt = """
                Settimana: %s.
                Impegni già noti: %s.
                Messaggio di %s: "%s"
                """.formatted(days, current, user.firstName(), message);

        return llm.chat(system, List.of(new LlmClient.Turn("user", prompt)), true)
                .map(r -> parseCommitments(r.text(), data))
                .orElse(List.of());
    }

    /** Risposta in chat, con il piano già ricalcolato dal motore dopo gli eventuali nuovi impegni. */
    public AiText chatReply(User user, Recommendation rec, OfficeData data, List<LlmClient.Turn> history,
                            List<DayStatus> applied) {
        String changes = applied.isEmpty() ? "nessuno" : applied.stream()
                .map(s -> dayLabel(s.date()) + ": " + needLabel(s.officeNeed()) + (s.note() == null ? "" : " (" + s.note() + ")"))
                .collect(Collectors.joining("; "));
        String system = SYSTEM + "\n" + context(user, rec, data) + """

                Impegni appena registrati dall'ultimo messaggio dell'utente: %s.
                Rispondi in massimo 4 frasi. Se ci sono nuovi impegni confermali e spiega come cambia il piano.
                Se l'utente chiede di prenotare o annullare, ricordagli che può farlo con i bottoni del piano qui sotto.
                Se la domanda non riguarda ufficio, colleghi o settimana lavorativa, riportala gentilmente sul tema.
                """.formatted(changes);

        List<LlmClient.Turn> turns = history.size() > MAX_HISTORY
                ? history.subList(history.size() - MAX_HISTORY, history.size())
                : history;
        return llm.chat(system, turns, false)
                .map(r -> new AiText(r.text(), "llm", r.model()))
                .orElseGet(() -> new AiText(templateReply(user, rec, data, applied), "template", "template"));
    }

    public AiText inviteDraft(User from, List<User> to, DayRecommendation day, OfficeData data) {
        String names = to.stream().map(User::firstName).collect(Collectors.joining(", "));
        String prompt = """
                Scrivi un breve messaggio Teams (massimo 4 frasi) da %s a %s
                per invitarli in ufficio %s nell'area %s.
                Contesto: %s
                Il messaggio deve essere cordiale, concreto e chiudersi con la firma "%s".
                """.formatted(from.fullName(), names, dayLabel(day.date()), areaName(day.areaId(), data),
                String.join("; ", day.reasons()), from.firstName());

        return llm.complete(SYSTEM, prompt)
                .map(r -> new AiText(r.text(), "llm", r.model()))
                .orElseGet(() -> new AiText(templateInvite(from, names, day, data), "template", "template"));
    }

    // ---------------------------------------------------------------- contesto per l'LLM

    private String context(User user, Recommendation rec, OfficeData data) {
        String facts = rec.days().stream()
                .map(d -> "%s (%s): %s, area %s, %s%s. Motivi: %s.%s".formatted(
                        dayLabel(d.date()), d.date(), d.verdict(), areaName(d.areaId(), data),
                        d.areaFull() ? "area al completo" : d.areaFreeSeats() + " posti liberi",
                        d.alreadyBooked() ? ", GIÀ PRENOTATO" : "",
                        String.join("; ", d.reasons()),
                        d.inviteUserIds().isEmpty() ? "" : " Colleghi da invitare: " + d.inviteUserIds().stream()
                                .map(id -> data.user(id).map(User::fullName).orElse(id))
                                .collect(Collectors.joining(", ")) + "."))
                .collect(Collectors.joining("\n"));
        return """
                Utente loggato: %s, %s, skill %s, team %s, giorni minimi in ufficio: %d.
                Piano della settimana calcolato dal motore:
                %s
                """.formatted(user.fullName(), user.seniority(), user.skills(), user.team(), user.minOfficeDays(), facts);
    }

    private List<DayStatus> parseCommitments(String raw, OfficeData data) {
        try {
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return List.of();
            }
            JsonNode items = mapper.readTree(json.substring(start, end + 1)).path("commitments");
            List<DayStatus> out = new ArrayList<>();
            for (JsonNode item : items) {
                String date = item.path("date").asText("");
                if (!data.week().contains(date)) {
                    continue;
                }
                OfficeNeed need;
                try {
                    need = OfficeNeed.valueOf(item.path("officeNeed").asText("").trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String note = item.path("note").asText("").trim();
                out.add(new DayStatus(date, need, note.isEmpty() ? null : note));
            }
            return out;
        } catch (Exception e) {
            log.warn("Impegni non interpretabili: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- template di fallback

    private String templateOpening(User user, Recommendation rec, OfficeData data) {
        List<DayRecommendation> go = rec.goDays();
        if (go.isEmpty()) {
            return "Ciao " + user.firstName() + ", questa settimana non ci sono motivi forti per andare in ufficio: "
                    + "goditi il lavoro focalizzato da remoto. Scrivimi pure i tuoi impegni e aggiorno il piano.";
        }
        String days = go.stream().map(d -> dayLabel(d.date())).collect(Collectors.joining(" e "));
        DayRecommendation best = go.get(0);
        String why = best.reasons().isEmpty() ? "" : " Il motivo principale: " + best.reasons().get(0) + ".";
        return "Ciao " + user.firstName() + ", ti consiglio l'ufficio " + days + " in "
                + areaName(best.areaId(), data) + "." + why + " Scrivimi pure i tuoi impegni e aggiorno il piano.";
    }

    private String templateReply(User user, Recommendation rec, OfficeData data, List<DayStatus> applied) {
        String days = rec.goDays().stream().map(d -> dayLabel(d.date())).collect(Collectors.joining(" e "));
        return "L'assistente AI non è raggiungibile in questo momento, " + user.firstName() + ". "
                + (days.isEmpty() ? "Il piano attuale ti consiglia di restare in remoto." : "Il piano attuale ti consiglia l'ufficio " + days + ".");
    }

    private String templateInvite(User from, String names, DayRecommendation day, OfficeData data) {
        return "Ciao " + names + "! " + capitalize(dayLabel(day.date())) + " sarò in ufficio in "
                + areaName(day.areaId(), data) + ": vi va di esserci anche voi? "
                + "Sarebbe un'ottima occasione per lavorare insieme di persona. A presto, " + from.firstName();
    }

    // ---------------------------------------------------------------- helpers

    private static String needLabel(OfficeNeed need) {
        return switch (need) {
            case REQUIRED -> "in ufficio (obbligatorio)";
            case OPTIONAL -> "evento facoltativo in ufficio";
            case REMOTE -> "da remoto";
            case FREE -> "impegno annullato";
        };
    }

    private static String dayLabel(String isoDate) {
        LocalDate date = LocalDate.parse(isoDate);
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + date.getDayOfMonth();
    }

    private static String areaName(String areaId, OfficeData data) {
        return areaId == null ? "nessuna" : data.area(areaId).map(a -> a.name()).orElse(areaId);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
