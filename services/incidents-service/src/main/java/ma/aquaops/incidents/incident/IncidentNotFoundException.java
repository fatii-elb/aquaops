package ma.aquaops.incidents.incident;

public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(Long id) {
        super("Incident " + id + " introuvable");
    }
}
