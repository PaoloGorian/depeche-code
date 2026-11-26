package it.depechecode.officeplanner.domain;

import java.util.List;

/**
 * Raccomandazione per un singolo giorno.
 * areaId è l'area consigliata (null se non ci sono aree compatibili).
 */
public record DayRecommendation(
        String date,
        Verdict verdict,
        OfficeNeed officeNeed,
        String note,
        String areaId,
        int score,
        List<String> reasons,
        List<String> inviteUserIds,
        int teamPresent,
        int areaFreeSeats,
        boolean alreadyBooked,
        boolean areaFull
) {
}
