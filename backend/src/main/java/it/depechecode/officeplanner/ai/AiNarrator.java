package it.depechecode.officeplanner.ai;

import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Trasforma l'output deterministico del motore in testo naturale tramite LLM.
 * Il motore decide, l'LLM spiega e scrive: così il risultato resta stabile e verificabile.
 */
public class AiNarrator {

    private static final String SYSTEM = """
            Sei l'assistente di "Should I Stay or Should I Go?", lo Smart Office Planner del team Depeche Code.
            Aiuti i dipendenti a scegliere i giorni in ufficio per incontrare le persone giuste.
            Rispondi sempre in italiano, con tono amichevole e professionale, dando del tu.
            Usa solo i fatti forniti, senza inventare persone, eventi o numeri.
            Non usare elenchi puntati, markdown o trattini lunghi.
            """;

    private final LlmClient llm;

    public AiNarrator(LlmClient llm) {
        this.llm = llm;
    }

    public AiText weekSummary(User user, Recommendation rec, OfficeData data) {
        String facts = rec.days().stream()
                .map(d -> dayLabel(d.date()) + ": " + d.verdict() + " (punteggio " + d.score() + ", area "
                        + areaName(d.areaId(), data) + "). Motivi: " + String.join("; ", d.reasons()))
                .collect(Collectors.joining("\n"));
        String prompt = """
                Utente: %s, %s, skill %s, team %s, giorni minimi in ufficio: %d.
                Raccomandazione della settimana calcolata dal motore:
                %s

                Scrivi un consiglio di massimo 3 frasi: quali giorni andare in ufficio e perché,
                citando i colleghi chiave e l'eventuale mentorship. Se ci sono giorni STAY, consiglia il lavoro focalizzato da remoto.
                """.formatted(user.fullName(), user.seniority(), user.skills(), user.team(),
                user.minOfficeDays(), facts);

        return llm.complete(SYSTEM, prompt)
                .map(t -> new AiText(t, "llm", llm.providerName()))
                .orElseGet(() -> new AiText(templateSummary(user, rec, data), "template", "template"));
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
                .map(t -> new AiText(t, "llm", llm.providerName()))
                .orElseGet(() -> new AiText(templateInvite(from, names, day, data), "template", "template"));
    }

    // ---------------------------------------------------------------- template di fallback

    private String templateSummary(User user, Recommendation rec, OfficeData data) {
        List<DayRecommendation> go = rec.goDays();
        if (go.isEmpty()) {
            return "Ciao " + user.firstName() + ", questa settimana non ci sono motivi forti per andare in ufficio: "
                    + "goditi il lavoro focalizzato da remoto.";
        }
        String days = go.stream().map(d -> dayLabel(d.date())).collect(Collectors.joining(" e "));
        DayRecommendation best = go.get(0);
        String why = best.reasons().isEmpty() ? "" : " Il motivo principale: " + best.reasons().get(0) + ".";
        return "Ciao " + user.firstName() + ", ti consiglio l'ufficio " + days + " in "
                + areaName(best.areaId(), data) + "." + why + " Negli altri giorni resta da remoto per il lavoro focalizzato.";
    }

    private String templateInvite(User from, String names, DayRecommendation day, OfficeData data) {
        return "Ciao " + names + "! " + capitalize(dayLabel(day.date())) + " sarò in ufficio in "
                + areaName(day.areaId(), data) + ": vi va di esserci anche voi? "
                + "Sarebbe un'ottima occasione per lavorare insieme di persona. A presto, " + from.firstName();
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
