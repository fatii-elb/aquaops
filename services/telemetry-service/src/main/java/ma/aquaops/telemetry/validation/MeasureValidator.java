package ma.aquaops.telemetry.validation;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valide une mesure selon le contrat sensor-readings v1 (SCRUM-26).
 * Classe pure : pas de Spring, ni Kafka, ni base. Les champs inconnus sont
 * ignorés, comme le demande la règle de versionnement des événements.
 */
public class MeasureValidator {

    private static final Set<String> TYPES = Set.of("PRESSURE", "FLOW", "LEVEL", "PUMP_STATE");
    private static final Set<String> PUMP_STATES = Set.of("NORMAL", "FAULT");

    public ValidationResult validate(Map<String, Object> mesure) {
        List<String> erreurs = new ArrayList<>();
        if (mesure == null) {
            erreurs.add("message vide");
            return new ValidationResult(erreurs);
        }

        if (!"1".equals(mesure.get("eventVersion"))) {
            erreurs.add("eventVersion doit valoir \"1\"");
        }
        verifierIdentifiant(mesure, "sensorId", erreurs);
        verifierIdentifiant(mesure, "zoneId", erreurs);

        Object type = mesure.get("type");
        if (!(type instanceof String texte) || !TYPES.contains(texte)) {
            erreurs.add("type invalide (PRESSURE, FLOW, LEVEL ou PUMP_STATE)");
        } else if ("PUMP_STATE".equals(texte)) {
            Object etat = mesure.get("pumpState");
            if (!(etat instanceof String e) || !PUMP_STATES.contains(e)) {
                erreurs.add("pumpState obligatoire pour PUMP_STATE (NORMAL ou FAULT)");
            }
        } else {
            verifierValeur(mesure, erreurs);
        }

        if (mesure.containsKey("unit") && !(mesure.get("unit") instanceof String)) {
            erreurs.add("unit doit être un texte");
        }
        verifierDate(mesure, erreurs);
        return new ValidationResult(erreurs);
    }

    private void verifierIdentifiant(Map<String, Object> mesure, String champ, List<String> erreurs) {
        Object v = mesure.get(champ);
        boolean entier = v instanceof Integer || v instanceof Long;
        if (!entier || ((Number) v).longValue() <= 0) {
            erreurs.add(champ + " doit être un entier positif");
        }
    }

    // Hypothèse à confirmer avec l'équipe : pression, débit et niveau ne sont jamais négatifs.
    private void verifierValeur(Map<String, Object> mesure, List<String> erreurs) {
        Object v = mesure.get("value");
        if (!(v instanceof Number n)) {
            erreurs.add("value obligatoire (nombre) pour ce type de mesure");
        } else if (Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue()) || n.doubleValue() < 0) {
            erreurs.add("value doit être un nombre fini et positif ou nul");
        }
    }

    private void verifierDate(Map<String, Object> mesure, List<String> erreurs) {
        Object d = mesure.get("measuredAt");
        if (!(d instanceof String texte)) {
            erreurs.add("measuredAt obligatoire (date ISO 8601 UTC)");
            return;
        }
        try {
            Instant.parse(texte);
        } catch (DateTimeParseException e) {
            erreurs.add("measuredAt n'est pas une date ISO 8601 valide");
        }
    }
}