package it.depechecode.officeplanner.chat;

import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.User;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser a regole, senza AI: fallback quando l'LLM non è configurato o non risponde.
 * Riconosce persona (nome o nome e cognome), giorno (oggi, domani, nome del giorno) e tipo di impegno.
 */
public class RuleBasedCommandParser {

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "luned", DayOfWeek.MONDAY,
            "marted", DayOfWeek.TUESDAY,
            "mercoled", DayOfWeek.WEDNESDAY,
            "gioved", DayOfWeek.THURSDAY,
            "venerd", DayOfWeek.FRIDAY);

    private static final List<String> FREE_WORDS = List.of(
            "nessun impegno", "libero", "libera", "annull", "cancell", "rimuov", "elimin", "da remoto", "in remoto");
    private static final List<String> OPTIONAL_WORDS = List.of(
            "non obbligatori", "facoltativ", "opzional", "jolly");

    private static final Pattern NOTE = Pattern.compile(
            "\\b(?:ha|ho|avrà|avra|c'è)\\s+(?:(?:una|uno|un|il|la|lo)\\s+|l'|un')?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_TAIL = Pattern.compile(
            "\\s+(?:oggi|domani|luned\\S*|marted\\S*|mercoled\\S*|gioved\\S*|venerd\\S*|che|in presenza|in ufficio)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    public Optional<ChatCommand> parse(String message, String fallbackUserId, String today, OfficeData data) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String text = message.toLowerCase(Locale.ITALIAN).trim();
        Optional<User> named = findUser(text, data);
        boolean mentionsDay = text.contains("oggi") || text.contains("domani")
                || DAYS.keySet().stream().anyMatch(text::contains);
        boolean mentionsCommitment = Pattern.compile("\\b(ha|ho|impegn\\w*|annull\\w*|cancell\\w*|libero|libera)\\b")
                .matcher(text).find();
        if (!mentionsCommitment || (named.isEmpty() && !mentionsDay && !text.startsWith("ho "))) {
            return Optional.empty();
        }

        String userId = named.map(User::id).orElse(fallbackUserId);
        if (userId == null) {
            return Optional.empty();
        }
        String date = findDate(text, today, data.week());
        OfficeNeed need = findNeed(text);
        String note = need == OfficeNeed.FREE ? null : findNote(message);
        return Optional.of(new ChatCommand(userId, date, need, note, "regole"));
    }

    private Optional<User> findUser(String text, OfficeData data) {
        Optional<User> byFullName = data.users().stream()
                .filter(u -> text.contains(u.fullName().toLowerCase(Locale.ITALIAN)))
                .findFirst();
        if (byFullName.isPresent()) {
            return byFullName;
        }
        return data.users().stream()
                .filter(u -> Pattern.compile("\\b" + Pattern.quote(u.firstName().toLowerCase(Locale.ITALIAN)) + "\\b")
                        .matcher(text).find())
                .min(Comparator.comparing(User::id));
    }

    private String findDate(String text, String today, List<String> week) {
        if (text.contains("domani")) {
            int i = week.indexOf(today);
            return i >= 0 && i + 1 < week.size() ? week.get(i + 1) : today;
        }
        for (Map.Entry<String, DayOfWeek> e : DAYS.entrySet()) {
            if (text.contains(e.getKey())) {
                return week.stream()
                        .filter(d -> LocalDate.parse(d).getDayOfWeek() == e.getValue())
                        .findFirst()
                        .orElse(today);
            }
        }
        return today;
    }

    private OfficeNeed findNeed(String text) {
        if (OPTIONAL_WORDS.stream().anyMatch(text::contains)) {
            return OfficeNeed.OPTIONAL;
        }
        if (FREE_WORDS.stream().anyMatch(text::contains)) {
            return OfficeNeed.FREE;
        }
        return OfficeNeed.REQUIRED;
    }

    private String findNote(String message) {
        Matcher m = NOTE.matcher(message.trim());
        if (!m.find()) {
            return null;
        }
        String note = NOTE_TAIL.matcher(m.group(1)).replaceAll("")
                .replaceAll("(?i)\\b(non )?obbligatori[oa]\\b", "")
                .replaceAll("(?i)\\bfacoltativ[oa]\\b", "")
                .replaceAll("[.!?,;]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (note.isEmpty()) {
            return null;
        }
        note = note.length() > 60 ? note.substring(0, 60) : note;
        return Character.toUpperCase(note.charAt(0)) + note.substring(1);
    }
}
