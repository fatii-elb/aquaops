package ma.aquaops.detection.engine;

/**
 * Prevenu quand une anomalie ouverte revient a la normale (BE-1 : passage au statut RESOLVED).
 * Les deux methodes sont facultatives : le moteur de detection fonctionne sans ecouteur.
 */
public interface AnomalyRecoveryListener {

    /** Une anomalie de zone (fuite, pompe en defaut) est terminee. */
    default void onRecovered(int zoneId, String type) {}

    /** Un capteur silencieux a de nouveau emis une mesure. */
    default void onSensorRecovered(int zoneId, int sensorId) {}
}
