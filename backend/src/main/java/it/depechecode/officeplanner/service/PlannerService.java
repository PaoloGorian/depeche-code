package it.depechecode.officeplanner.service;

import it.depechecode.officeplanner.ai.AiNarrator;
import it.depechecode.officeplanner.ai.AiText;
import it.depechecode.officeplanner.domain.Area;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.engine.RecommendationEngine;
import it.depechecode.officeplanner.repository.OfficeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Casi d'uso dell'applicazione: orchestra repository, motore e AI. */
@Service
public class PlannerService {

    private final OfficeRepository repository;
    private final RecommendationEngine engine;
    private final AiNarrator narrator;

    public PlannerService(OfficeRepository repository, RecommendationEngine engine, AiNarrator narrator) {
        this.repository = repository;
        this.engine = engine;
        this.narrator = narrator;
    }

    public OfficeData data() {
        return repository.snapshot();
    }

    /** Oggi, se cade nella settimana dei dati; altrimenti il primo giorno della settimana (demo). */
    public String today() {
        List<String> week = repository.snapshot().week();
        String now = LocalDate.now().toString();
        return week.contains(now) || week.isEmpty() ? now : week.get(0);
    }

    public Recommendation recommend(String userId) {
        OfficeData data = repository.snapshot();
        return engine.recommend(requireUser(data, userId), data);
    }

    public AiText summary(String userId) {
        return summary(userId, null);
    }

    public AiText summary(String userId, String latestChange) {
        OfficeData data = repository.snapshot();
        User user = requireUser(data, userId);
        return narrator.weekSummary(user, engine.recommend(user, data), data, latestChange);
    }

    public AiText aiPing() {
        return narrator.ping();
    }

    public AiText inviteDraft(String fromUserId, String date, List<String> toUserIds) {
        OfficeData data = repository.snapshot();
        User from = requireUser(data, fromUserId);
        List<User> to = toUserIds.stream().map(id -> requireUser(data, id)).toList();
        DayRecommendation day = engine.recommend(from, data).days().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Data fuori settimana: " + date));
        return narrator.inviteDraft(from, to, day, data);
    }

    /** Prenota l'utente nell'area indicata, o nella migliore disponibile se areaId è nullo. */
    public synchronized Booking book(String userId, String date, String areaId) {
        OfficeData data = repository.snapshot();
        User user = requireUser(data, userId);
        if (!data.week().contains(date)) {
            throw new IllegalArgumentException("Data fuori settimana: " + date);
        }
        if (data.bookingOf(userId, date).isPresent()) {
            throw new IllegalStateException(user.fullName() + " ha già una prenotazione per " + date);
        }
        Area area = Optional.ofNullable(areaId)
                .flatMap(data::area)
                .or(() -> engine.bestAreaFor(user, date, data))
                .orElseThrow(() -> new IllegalStateException("Nessuna area disponibile per " + user.fullName()));
        if (data.occupiedSeats(area.id(), date) >= area.seats()) {
            throw new IllegalStateException(area.name() + " è al completo il " + date);
        }
        Booking booking = new Booking(userId, date, area.id());
        repository.addBooking(booking);
        return booking;
    }

    /** Simula l'accettazione degli inviti: prenota ogni collega nella sua area migliore. */
    public List<Booking> acceptInvites(String date, List<String> userIds) {
        List<Booking> created = new ArrayList<>();
        for (String id : userIds) {
            try {
                created.add(book(id, date, null));
            } catch (IllegalStateException ignored) {
                // già prenotato o area piena: si salta
            }
        }
        return created;
    }

    public void reset() {
        repository.reset();
    }

    private static User requireUser(OfficeData data, String id) {
        return data.user(id).orElseThrow(() -> new NoSuchElementException("Utente non trovato: " + id));
    }
}
