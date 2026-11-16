
package ma.aquaops.detection.engine;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class AnomalyDeduplicator {

    private record Key(int zoneId, String type) {}

    private final Set<Key> openAnomalies =
            new HashSet<>();

    // Retourne true uniquement lors de la
    // premiere apparition de l'anomalie.
    public synchronized boolean tryOpen(
            int zoneId, String type) {

        return openAnomalies.add(
                new Key(
                        zoneId,
                        Objects.requireNonNull(type)
                )
        );
    }

    // Apres recuperation, une nouvelle anomalie
    // du meme type pourra etre detectee.
    // Retourne true si une anomalie etait bien ouverte.
    public synchronized boolean recover(
            int zoneId, String type) {

        return openAnomalies.remove(
                new Key(
                        zoneId,
                        Objects.requireNonNull(type)
                )
        );
    }

    // Cle stable partageable avec les autres services.
    public static String dedupKey(
            int zoneId, String type) {

        return "zone:" + zoneId + ":" +
                Objects.requireNonNull(type);
    }
}
