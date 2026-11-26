package it.depechecode.officeplanner.engine;

import it.depechecode.officeplanner.domain.Area;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.Seniority;
import it.depechecode.officeplanner.domain.Skill;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.domain.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationEngineTest {

    private static final String MON = "2026-11-30";
    private static final String TUE = "2026-12-01";
    private static final String WED = "2026-12-02";

    private final RecommendationEngine engine = new RecommendationEngine(ScoringWeights.defaults());

    private final User junior = new User("j", "Chiara", "Neri", "c@x", Seniority.JUNIOR, List.of(Skill.FE),
            "Payments", 1, List.of(new DayStatus(MON, OfficeNeed.FREE, null)));
    private final User senior = new User("s", "Marco", "Bianchi", "m@x", Seniority.SENIOR, List.of(Skill.FE),
            "Payments", 2, List.of());
    private final User remote = new User("r", "Federica", "Gatti", "f@x", Seniority.SENIOR, List.of(Skill.BE),
            "IoT", 0, List.of(new DayStatus(WED, OfficeNeed.REQUIRED, "Visita del management")));

    private final Area fe = new Area("a-fe", "Frontend Island", Skill.FE, 2);
    private final Area be = new Area("a-be", "Backend Bay", Skill.BE, 2);

    @Test
    void juniorGoesWhereTheSeniorOfTheTeamIs() {
        OfficeData data = data(List.of(new Booking("s", TUE, "a-fe")));

        Recommendation rec = engine.recommend(junior, data);

        DayRecommendation tue = day(rec, TUE);
        assertEquals(Verdict.GO, tue.verdict());
        assertEquals("a-fe", tue.areaId());
        assertTrue(tue.reasons().stream().anyMatch(r -> r.startsWith("Mentorship")));
        assertEquals(1, rec.goDays().size());
    }

    @Test
    void fullAreaIsNeverRecommended() {
        User other = new User("o", "Elena", "Riva", "e@x", Seniority.SENIOR, List.of(Skill.FE), "Mobile", 1, List.of());
        OfficeData data = data(List.of(new Booking("s", TUE, "a-fe"), new Booking("o", TUE, "a-fe")), other);

        DayRecommendation tue = day(engine.recommend(junior, data), TUE);

        assertTrue(tue.areaFull());
        assertEquals(Verdict.STAY, tue.verdict());
    }

    @Test
    void fullRemoteGoesOnlyOnRequiredDays() {
        Recommendation rec = engine.recommend(remote, data(List.of()));

        assertEquals(List.of(WED), rec.goDays().stream().map(DayRecommendation::date).toList());
    }

    @Test
    void remoteCommitmentMovesTheGoDayElsewhere() {
        OfficeData data = data(List.of(new Booking("s", TUE, "a-fe")));
        User busy = new User("j", "Chiara", "Neri", "c@x", Seniority.JUNIOR, List.of(Skill.FE), "Payments", 1,
                List.of(new DayStatus(TUE, OfficeNeed.REMOTE, "Visita medica")));

        Recommendation rec = engine.recommend(busy, data);

        assertEquals(Verdict.STAY, day(rec, TUE).verdict());
        assertEquals(1, rec.goDays().size());
        assertTrue(day(rec, TUE).reasons().stream().anyMatch(r -> r.contains("Visita medica")));
    }

    private OfficeData data(List<Booking> bookings, User... extra) {
        List<User> users = new java.util.ArrayList<>(List.of(junior, senior, remote));
        users.addAll(List.of(extra));
        return new OfficeData(List.of(MON, TUE, WED), users, List.of(fe, be), bookings);
    }

    private static DayRecommendation day(Recommendation rec, String date) {
        return rec.days().stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }
}
