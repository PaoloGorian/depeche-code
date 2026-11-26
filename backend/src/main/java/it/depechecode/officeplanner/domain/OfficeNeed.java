package it.depechecode.officeplanner.domain;

/**
 * Esigenza di presenza dell'utente in un giorno.
 * REMOTE = non può venire in ufficio (permesso, trasferta, impegno da casa): il motore non lo propone mai.
 */
public enum OfficeNeed { REQUIRED, OPTIONAL, FREE, REMOTE }
