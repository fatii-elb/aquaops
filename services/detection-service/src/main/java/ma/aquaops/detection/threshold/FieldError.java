package ma.aquaops.detection.threshold;

/** Erreur de validation d'un champ, renvoyee en 400 : {"errors":[{"field":"...","message":"..."}]}. */
public record FieldError(String field, String message) {
}
