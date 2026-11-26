package it.depechecode.officeplanner.engine;

/**
 * Pesi dello scoring. Configurabili da application.properties (planner.weights.*),
 * così il business può tararli senza toccare il codice.
 */
public record ScoringWeights(
        int required,
        int optional,
        int teamMember,
        int sameSkillInArea,
        int mentorship
) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(100, 15, 10, 3, 15);
    }
}
