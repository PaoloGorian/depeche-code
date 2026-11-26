package it.depechecode.officeplanner.engine;

import it.depechecode.officeplanner.domain.Area;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.domain.Verdict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Motore di raccomandazione: deterministico, spiegabile, senza dipendenze da framework.
 * Riceve una fotografia dei dati (OfficeData) e restituisce un verdetto GO/STAY per ogni giorno.
 */
public final class RecommendationEngine {

    private static final int MAX_INVITES = 4;

    private final ScoringWeights weights;

    public RecommendationEngine(ScoringWeights weights) {
        this.weights = weights;
    }

    public Recommendation recommend(User user, OfficeData data) {
        List<DayEvaluation> evaluations = data.week().stream()
                .map(date -> evaluate(user, date, data))
                .toList();

        Set<String> goDates = chooseGoDays(user, evaluations);

        List<DayRecommendation> days = evaluations.stream()
                .map(e -> {
                    boolean go = goDates.contains(e.date());
                    List<String> invites = go ? invitesFor(user, e.date(), data) : List.of();
                    return e.toRecommendation(go ? Verdict.GO : Verdict.STAY, invites);
                })
                .toList();

        return new Recommendation(user.id(), user.minOfficeDays(), days);
    }

    /** Area migliore per un utente in una data, usata anche per accettare gli inviti. */
    public Optional<Area> bestAreaFor(User user, String date, OfficeData data) {
        return rankAreas(user, date, data).stream()
                .filter(c -> c.freeSeats() > 0)
                .map(AreaCandidate::area)
                .findFirst();
    }

    // ---------------------------------------------------------------- scoring

    private DayEvaluation evaluate(User user, String date, OfficeData data) {
        DayStatus status = user.statusOn(date);
        OfficeNeed need = status.officeNeed();
        Optional<Booking> ownBooking = data.bookingOf(user.id(), date);

        List<User> others = data.bookingsOn(date).stream()
                .filter(b -> !b.userId().equals(user.id()))
                .map(b -> data.user(b.userId()).orElse(null))
                .filter(u -> u != null)
                .toList();
        List<User> teamPresent = others.stream().filter(u -> u.team().equals(user.team())).toList();

        AreaCandidate area = ownBooking
                .flatMap(b -> data.area(b.areaId()))
                .map(a -> candidate(user, a, date, data))
                .orElseGet(() -> rankAreas(user, date, data).stream().findFirst().orElse(null));

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (need == OfficeNeed.REQUIRED) {
            score += weights.required();
            reasons.add("Impegno che richiede la presenza in ufficio" + noteSuffix(status));
        } else if (need == OfficeNeed.OPTIONAL) {
            score += weights.optional();
            reasons.add("Evento facoltativo utile in presenza" + noteSuffix(status));
        } else if (need == OfficeNeed.REMOTE) {
            reasons.add("Non puoi venire in ufficio" + noteSuffix(status));
        }

        if (!teamPresent.isEmpty()) {
            score += weights.teamMember() * teamPresent.size();
            reasons.add(colleagues(teamPresent.size()) + " del team " + user.team() + " in ufficio: " + names(teamPresent));
        }

        boolean areaFull = area == null || area.freeSeats() <= 0;
        if (area != null) {
            if (area.sameSkill() > 0) {
                score += weights.sameSkillInArea() * area.sameSkill();
                reasons.add(colleagues(area.sameSkill()) + " " + area.area().skill() + " in " + area.area().name());
            }
            if (!area.mentors().isEmpty()) {
                score += weights.mentorship();
                reasons.add("Mentorship: senior presenti in area (" + names(area.mentors()) + ")");
            }
            if (!area.mentees().isEmpty()) {
                score += weights.mentorship();
                reasons.add("Puoi affiancare i junior in area (" + names(area.mentees()) + ")");
            }
            if (areaFull) {
                reasons.add(area.area().name() + " al completo");
            } else {
                reasons.add((area.freeSeats() == 1 ? "1 posto libero" : area.freeSeats() + " posti liberi") + " in " + area.area().name());
            }
        } else {
            reasons.add("Nessuna area compatibile con le tue skill");
        }

        if (ownBooking.isPresent()) {
            reasons.add(0, "Hai già prenotato questo giorno");
        }

        return new DayEvaluation(date, need, status.note(), area, score, reasons,
                teamPresent.size(), ownBooking.isPresent(), areaFull);
    }

    /**
     * Sceglie i giorni GO: prima quelli già prenotati e quelli REQUIRED,
     * poi i migliori per punteggio fino a minOfficeDays, scartando le aree piene.
     */
    private Set<String> chooseGoDays(User user, List<DayEvaluation> evaluations) {
        Set<String> go = new LinkedHashSet<>();
        evaluations.stream()
                .filter(e -> e.alreadyBooked() || e.need() == OfficeNeed.REQUIRED)
                .forEach(e -> go.add(e.date()));

        int missing = user.minOfficeDays() - go.size();
        if (missing > 0) {
            evaluations.stream()
                    .filter(e -> !go.contains(e.date()))
                    .filter(e -> e.need() != OfficeNeed.REMOTE)
                    .filter(e -> !e.areaFull())
                    .sorted(Comparator.comparingInt(DayEvaluation::score).reversed()
                            .thenComparing(DayEvaluation::date))
                    .limit(missing)
                    .forEach(e -> go.add(e.date()));
        }
        return go;
    }

    /**
     * Colleghi da invitare: membri del team non ancora prenotati con giornata facoltativa o libera;
     * per i junior anche i senior della stessa skill.
     */
    private List<String> invitesFor(User user, String date, OfficeData data) {
        Set<String> booked = new HashSet<>();
        data.bookingsOn(date).forEach(b -> booked.add(b.userId()));

        Comparator<User> byNeed = Comparator.comparingInt(u -> u.needOn(date) == OfficeNeed.OPTIONAL ? 0 : 1);

        List<User> candidates = data.users().stream()
                .filter(u -> !u.id().equals(user.id()))
                .filter(u -> !booked.contains(u.id()))
                .filter(u -> u.needOn(date) != OfficeNeed.REQUIRED && u.needOn(date) != OfficeNeed.REMOTE)
                .toList();

        Set<String> invites = new LinkedHashSet<>();
        candidates.stream()
                .filter(u -> u.team().equals(user.team()))
                .sorted(byNeed)
                .forEach(u -> invites.add(u.id()));
        if (user.isJunior()) {
            candidates.stream()
                    .filter(User::isSenior)
                    .filter(u -> u.skills().stream().anyMatch(user::hasSkill))
                    .sorted(byNeed)
                    .forEach(u -> invites.add(u.id()));
        }
        return invites.stream().limit(MAX_INVITES).toList();
    }

    // ---------------------------------------------------------------- aree

    private List<AreaCandidate> rankAreas(User user, String date, OfficeData data) {
        return data.areas().stream()
                .filter(a -> user.hasSkill(a.skill()))
                .map(a -> candidate(user, a, date, data))
                .sorted(Comparator.comparing((AreaCandidate c) -> c.freeSeats() > 0).reversed()
                        .thenComparing(Comparator.comparingInt(AreaCandidate::teamInArea).reversed())
                        .thenComparing(Comparator.comparingInt(AreaCandidate::sameSkill).reversed())
                        .thenComparing(Comparator.comparingInt(AreaCandidate::freeSeats).reversed()))
                .toList();
    }

    private AreaCandidate candidate(User user, Area area, String date, OfficeData data) {
        List<User> inArea = data.bookingsOn(date).stream()
                .filter(b -> b.areaId().equals(area.id()) && !b.userId().equals(user.id()))
                .map(b -> data.user(b.userId()).orElse(null))
                .filter(u -> u != null)
                .toList();
        int free = area.seats() - inArea.size();
        int team = (int) inArea.stream().filter(u -> u.team().equals(user.team())).count();
        int sameSkill = (int) inArea.stream().filter(u -> u.hasSkill(area.skill())).count();
        List<User> mentors = user.isJunior()
                ? inArea.stream().filter(User::isSenior).filter(u -> u.hasSkill(area.skill())).toList()
                : List.of();
        List<User> mentees = user.isSenior()
                ? inArea.stream().filter(User::isJunior).filter(u -> u.hasSkill(area.skill())).toList()
                : List.of();
        return new AreaCandidate(area, free, team, sameSkill, mentors, mentees);
    }

    // ---------------------------------------------------------------- helpers

    private static String names(List<User> users) {
        List<String> names = users.stream().map(User::fullName).toList();
        if (names.size() <= 3) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, 3)) + " e altri " + (names.size() - 3);
    }

    private static String colleagues(int n) {
        return n == 1 ? "1 collega" : n + " colleghi";
    }

    private static String noteSuffix(DayStatus status) {
        return status.note() == null || status.note().isBlank() ? "" : " (" + status.note() + ")";
    }

    private record AreaCandidate(Area area, int freeSeats, int teamInArea, int sameSkill,
                                 List<User> mentors, List<User> mentees) {
    }

    private record DayEvaluation(String date, OfficeNeed need, String note, AreaCandidate area, int score,
                                 List<String> reasons, int teamPresent, boolean alreadyBooked, boolean areaFull) {

        DayRecommendation toRecommendation(Verdict verdict, List<String> invites) {
            return new DayRecommendation(
                    date, verdict, need, note,
                    area == null ? null : area.area().id(),
                    score, List.copyOf(reasons), invites, teamPresent,
                    area == null ? 0 : Math.max(area.freeSeats(), 0),
                    alreadyBooked, areaFull);
        }
    }
}
