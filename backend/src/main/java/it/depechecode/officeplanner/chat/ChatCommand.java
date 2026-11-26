package it.depechecode.officeplanner.chat;

import it.depechecode.officeplanner.domain.OfficeNeed;

/**
 * Comando estratto da una frase in linguaggio naturale,
 * es. "oggi Chiara ha un workshop obbligatorio" -> (u01, 2026-11-26, REQUIRED, "Workshop").
 * parser: "llm" o "regole".
 */
public record ChatCommand(String userId, String date, OfficeNeed officeNeed, String note, String parser) {
}
