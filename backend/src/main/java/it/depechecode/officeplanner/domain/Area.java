package it.depechecode.officeplanner.domain;

/** Zona dell'ufficio dove lavorano persone con lo stesso background tecnico. */
public record Area(String id, String name, Skill skill, int seats) {
}
