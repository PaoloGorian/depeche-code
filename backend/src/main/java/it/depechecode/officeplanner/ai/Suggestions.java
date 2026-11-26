package it.depechecode.officeplanner.ai;

import java.util.List;

/** Suggerimenti di prompt per la chat. source: "llm" o "template". */
public record Suggestions(List<String> items, String source) {
}
