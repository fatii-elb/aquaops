package ma.aquaops.detection.anomaly;

/** Etat d'une anomalie conservee : ouverte tant que la situation anormale dure, resolue au retour a la normale. */
public enum AnomalyStatus {
    OPEN,
    RESOLVED
}
