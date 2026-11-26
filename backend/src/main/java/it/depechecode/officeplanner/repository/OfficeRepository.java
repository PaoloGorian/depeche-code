package it.depechecode.officeplanner.repository;

import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeData;

/**
 * Porta verso i dati. Nel PoC è un file JSON in memoria;
 * in produzione si sostituisce con un'implementazione su database (JPA, Postgres) senza toccare il motore.
 */
public interface OfficeRepository {

    OfficeData snapshot();

    void addBooking(Booking booking);

    /** Crea o aggiorna lo stato di un utente in un giorno (FREE equivale a rimuovere l'impegno). */
    void setDayStatus(String userId, DayStatus status);

    void reset();
}
