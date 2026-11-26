package it.depechecode.officeplanner.service;

import it.depechecode.officeplanner.ai.AiNarrator;
import it.depechecode.officeplanner.ai.AiText;
import it.depechecode.officeplanner.ai.LlmClient;
import it.depechecode.officeplanner.domain.Area;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayRecommendation;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.engine.RecommendationEngine;
import it.depechecode.officeplanner.repository.OfficeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public Recommendation recommend(String userId) {
        OfficeData data = repository.snapshot();
        return engine.recommend(requireUser(data, userId), data);
    }

    /**
     * Chat "Pianifica con l'AI".
     * Senza messaggi restituisce il consiglio iniziale. Altrimenti legge gli impegni dall'ultimo messaggio
     * dell'utente, li salva nel profilo, ricalcola il piano col motore e fa rispondere l'AI sul piano aggiornato.
     */
    public ChatResult chat(String userId, List<LlmClient.Turn> history) {
        OfficeData data = repository.snapshot();
        User user = requireUser(data, userId);
        List<LlmClient.Turn> turns = history == null ? List.of() : history.stream()
                .filter(t -> t.content() != null && !t.content().isBlank())
                .map(t -> new LlmClient.Turn("assistant".equals(t.role()) ? "assistant" : "user", t.content()))
                .toList();
        if (turns.isEmpty() || !"user".equals(turns.get(turns.size() - 1).role())) {
            return new ChatResult(narrator.opening(user, engine.recommend(user, data), data), List.of());
        }

        String last = turns.get(turns.size() - 1).content();
        User current = user;
        List<DayStatus> commitments = narrator.extractCommitments(user, last, data).stream()
                .filter(c -> isChange(current, c))
                .toList();
        if (!commitments.isEmpty()) {
            user = withCommitments(user, commitments);
            repository.updateUser(user);
            data = repository.snapshot();
        }
        AiText reply = narrator.chatReply(user, engine.recommend(user, data), data, turns, commitments);
        return new ChatResult(reply, commitments);
    }

    /** Risposta della chat: testo dell'AI e impegni registrati da questo messaggio (vuoto se nessuno). */
    public record ChatResult(AiText reply, List<DayStatus> commitments) {
    }

    /**
     * true se l'impegno cambia il tipo di presenza di quel giorno.
     * L'LLM a volte ripete impegni già noti riformulando la nota: quelli si scartano.
     */
    private static boolean isChange(User user, DayStatus c) {
        return user.statusOn(c.date()).officeNeed() != c.officeNeed();
    }

    /** Unisce i nuovi impegni al profilo: stessa data sostituisce, FREE cancella l'impegno. */
    private static User withCommitments(User user, List<DayStatus> commitments) {
        Map<String, DayStatus> byDate = new LinkedHashMap<>();
        user.availability().forEach(s -> byDate.put(s.date(), s));
        for (DayStatus c : commitments) {
            if (c.officeNeed() == OfficeNeed.FREE) {
                byDate.remove(c.date());
            } else {
                byDate.put(c.date(), c);
            }
        }
        List<DayStatus> availability = byDate.values().stream()
                .sorted(Comparator.comparing(DayStatus::date))
                .toList();
        return new User(user.id(), user.firstName(), user.lastName(), user.email(), user.seniority(),
                user.skills(), user.team(), user.minOfficeDays(), availability);
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

    /** Annulla la prenotazione dell'utente in quella data. */
    public synchronized void cancelBooking(String userId, String date) {
        OfficeData data = repository.snapshot();
        User user = requireUser(data, userId);
        if (!repository.removeBooking(userId, date)) {
            throw new NoSuchElementException(user.fullName() + " non ha prenotazioni per " + date);
        }
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
