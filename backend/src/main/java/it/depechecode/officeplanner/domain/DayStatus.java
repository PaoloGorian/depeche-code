package it.depechecode.officeplanner.domain;

/**
 * Stato dell'utente in un giorno.
 * date: ISO "YYYY-MM-DD". note: descrizione opzionale dell'impegno (es. "Workshop onboarding").
 */
public record DayStatus(String date, OfficeNeed officeNeed, String note) {

    public DayStatus {
        if (officeNeed == null) {
            officeNeed = OfficeNeed.FREE;
        }
    }
}
