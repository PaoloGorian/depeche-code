package it.depechecode.officeplanner.repository;

import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.OfficeData;
import it.depechecode.officeplanner.domain.User;

/**
 * Porta verso i dati. Nel PoC è un file JSON in memoria;
 * in produzione si sostituisce con un'implementazione su database (JPA, Postgres) senza toccare il motore.
 */
public interface OfficeRepository {

    OfficeData snapshot();

    void addBooking(Booking booking);

    /** Rimuove la prenotazione dell'utente in quella data; false se non c'era. */
    boolean removeBooking(String userId, String date);

    /** Sostituisce il profilo dell'utente con lo stesso id (es. dopo nuovi impegni). */
    void updateUser(User user);

    void reset();
}
