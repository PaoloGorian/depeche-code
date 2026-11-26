package it.depechecode.officeplanner.domain;

/** Prenotazione: un utente in un'area in una data ISO "YYYY-MM-DD". */
public record Booking(String userId, String date, String areaId) {
}
