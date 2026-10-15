package ma.aquaops.assets.model;

/** Statuts d'une intervention (les transitions autorisees viennent avec SCRUM-42). */
public enum InterventionStatus {
    PLANNED,      // Planifiee
    IN_PROGRESS,  // En cours
    DONE          // Terminee
}
