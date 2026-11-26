package it.depechecode.officeplanner.domain;

import java.util.List;

/** Output del motore: un verdetto GO/STAY per ogni giorno della settimana. */
public record Recommendation(String userId, int officeDaysTarget, List<DayRecommendation> days) {

    public List<DayRecommendation> goDays() {
        return days.stream().filter(d -> d.verdict() == Verdict.GO).toList();
    }
}
