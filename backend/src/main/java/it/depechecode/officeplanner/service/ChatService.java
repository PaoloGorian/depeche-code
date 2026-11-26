package it.depechecode.officeplanner.service;

import it.depechecode.officeplanner.ai.AiText;
import it.depechecode.officeplanner.chat.ChatCommand;
import it.depechecode.officeplanner.chat.LlmCommandParser;
import it.depechecode.officeplanner.chat.RuleBasedCommandParser;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.repository.OfficeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Chat in linguaggio naturale per aggiornare gli impegni ("oggi Chiara ha un workshop obbligatorio").
 * L'LLM interpreta la frase, il parser a regole fa da rete di sicurezza; poi si aggiorna il dato
 * e si rigenera sempre il consiglio della settimana per la persona interessata.
 */
@Service
public class ChatService {

    private final OfficeRepository repository;
    private final PlannerService planner;
    private final LlmCommandParser llmParser;
    private final RuleBasedCommandParser ruleParser;

    public ChatService(OfficeRepository repository, PlannerService planner,
                       LlmCommandParser llmParser, RuleBasedCommandParser ruleParser) {
        this.repository = repository;
        this.planner = planner;
        this.llmParser = llmParser;
        this.ruleParser = ruleParser;
    }

    public ChatResult handle(String currentUserId, String message) {
        OfficeData data = repository.snapshot();
        String today = planner.today();

        Optional<ChatCommand> command = llmParser.parse(message, currentUserId, today, data)
                .or(() -> ruleParser.parse(message, currentUserId, today, data));

        if (command.isEmpty()) {
            String target = currentUserId != null ? currentUserId : data.users().get(0).id();
            return new ChatResult(false,
                    "Non ho capito il comando. Prova con: \"oggi Chiara ha un workshop obbligatorio\".",
                    null, target, planner.summary(target));
        }

        ChatCommand c = command.get();
        repository.setDayStatus(c.userId(), new DayStatus(c.date(), c.officeNeed(), c.note()));
        User user = repository.snapshot().user(c.userId()).orElseThrow();

        return new ChatResult(true, confirmation(user, c), c, user.id(), planner.summary(user.id()));
    }

    private static String confirmation(User user, ChatCommand c) {
        LocalDate date = LocalDate.parse(c.date());
        String day = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + date.getDayOfMonth();
        String what = switch (c.officeNeed()) {
            case REQUIRED -> "impegno in presenza";
            case OPTIONAL -> "evento facoltativo";
            case FREE -> "nessun impegno";
        };
        String note = c.note() == null || c.officeNeed() == OfficeNeed.FREE ? "" : " (" + c.note() + ")";
        return "Aggiornato: " + user.fullName() + ", " + day + ", " + what + note + ".";
    }

    /** ok=false se la frase non è stata capita; summary è sempre il consiglio della settimana aggiornato. */
    public record ChatResult(boolean ok, String reply, ChatCommand command, String userId, AiText summary) {
    }
}
