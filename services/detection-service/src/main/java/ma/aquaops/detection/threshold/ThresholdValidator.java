package ma.aquaops.detection.threshold;

import java.util.ArrayList;
import java.util.List;

/**
 * Regles de validation des seuils. Memes bornes cote Angular (thresholds.rules.ts).
 * Un seuil absurde ferait taire la detection ou la rendrait inutilisable : on le refuse a l'enregistrement.
 */
public final class ThresholdValidator {

    public static final double MAX_PRESSURE = 100.0;
    public static final double MAX_FLOW = 100_000.0;
    public static final double MAX_LEVEL = 1_000.0;
    public static final long MAX_SECONDS = 3_600;

    private ThresholdValidator() {
    }

    /** Liste vide si les seuils sont valides. */
    public static List<FieldError> validate(ThresholdBody b) {
        List<FieldError> errors = new ArrayList<>();
        if (b == null) {
            errors.add(new FieldError("body", "Les seuils sont obligatoires."));
            return errors;
        }
        number(errors, "leakMinPressure", b.leakMinPressure(), 0, MAX_PRESSURE, true);
        number(errors, "leakMaxFlow", b.leakMaxFlow(), 0, MAX_FLOW, false);
        seconds(errors, "leakDurationSeconds", b.leakDurationSeconds());
        seconds(errors, "silenceThresholdSeconds", b.silenceThresholdSeconds());
        number(errors, "tankMinLevel", b.tankMinLevel(), 0, MAX_LEVEL, true);
        return errors;
    }

    /** Convertit un corps deja valide. */
    public static ZoneThresholds toThresholds(ThresholdBody b) {
        return new ZoneThresholds(b.leakMinPressure(), b.leakMaxFlow(), b.leakDurationSeconds(),
                b.silenceThresholdSeconds(), b.tankMinLevel());
    }

    private static void number(List<FieldError> errors, String field, Double value, double min, double max,
                               boolean minIncluded) {
        if (value == null) {
            errors.add(new FieldError(field, "Ce seuil est obligatoire."));
        } else if (!Double.isFinite(value)) {
            errors.add(new FieldError(field, "Ce seuil doit etre un nombre."));
        } else if (minIncluded ? value < min : value <= min) {
            errors.add(new FieldError(field, minIncluded
                    ? "Ce seuil ne peut pas etre negatif."
                    : "Ce seuil doit etre strictement positif."));
        } else if (value > max) {
            errors.add(new FieldError(field, "Ce seuil ne peut pas depasser " + max + "."));
        }
    }

    private static void seconds(List<FieldError> errors, String field, Long value) {
        if (value == null) {
            errors.add(new FieldError(field, "Cette duree est obligatoire."));
        } else if (value < 1 || value > MAX_SECONDS) {
            errors.add(new FieldError(field, "Cette duree doit etre comprise entre 1 et " + MAX_SECONDS + " secondes."));
        }
    }
}
