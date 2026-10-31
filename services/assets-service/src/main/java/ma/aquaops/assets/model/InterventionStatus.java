package ma.aquaops.assets.model;

/** Statuts d'une intervention et transitions autorisees (SCRUM-42, FR-20). */
public enum InterventionStatus {
    PLANNED,      // Planifiee
    IN_PROGRESS,  // En cours
    DONE;         // Terminee

    /** Planifiee -> En cours -> Terminee. Aucun retour en arriere, aucun saut. */
    public boolean canMoveTo(InterventionStatus next) {
        return switch (this) {
            case PLANNED -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == DONE;
            case DONE -> false;
        };
    }
}
