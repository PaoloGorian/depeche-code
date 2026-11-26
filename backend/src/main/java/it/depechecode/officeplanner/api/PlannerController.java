package it.depechecode.officeplanner.api;

import it.depechecode.officeplanner.ai.AiText;
import it.depechecode.officeplanner.domain.Area;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.Recommendation;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.service.ChatService;
import it.depechecode.officeplanner.service.PlannerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class PlannerController {

    private final PlannerService service;
    private final ChatService chat;

    public PlannerController(PlannerService service, ChatService chat) {
        this.service = service;
        this.chat = chat;
    }

    @GetMapping("/today")
    public Map<String, String> today() {
        return Map.of("date", service.today());
    }

    @PostMapping("/chat")
    public ChatService.ChatResult chat(@RequestBody ChatRequest request) {
        return chat.handle(request.userId(), request.message());
    }

    @GetMapping("/week")
    public List<String> week() {
        return service.data().week();
    }

    @GetMapping("/users")
    public List<User> users() {
        return service.data().users();
    }

    @GetMapping("/areas")
    public List<Area> areas() {
        return service.data().areas();
    }

    @GetMapping("/bookings")
    public List<Booking> bookings() {
        return service.data().bookings();
    }

    @GetMapping("/users/{id}/recommendation")
    public Recommendation recommendation(@PathVariable String id) {
        return service.recommend(id);
    }

    @GetMapping("/users/{id}/summary")
    public AiText summary(@PathVariable String id) {
        return service.summary(id);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Booking book(@RequestBody Booking request) {
        return service.book(request.userId(), request.date(), request.areaId());
    }

    @PostMapping("/invites/draft")
    public AiText inviteDraft(@RequestBody InviteRequest request) {
        return service.inviteDraft(request.fromUserId(), request.date(), request.toUserIds());
    }

    @PostMapping("/invites/accept")
    public List<Booking> acceptInvites(@RequestBody InviteRequest request) {
        return service.acceptInvites(request.date(), request.toUserIds());
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        service.reset();
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- errori

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    public record ChatRequest(String userId, String message) {
    }

    public record InviteRequest(String fromUserId, String date, List<String> toUserIds) {
    }
}
