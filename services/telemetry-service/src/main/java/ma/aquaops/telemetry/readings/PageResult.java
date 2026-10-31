package ma.aquaops.telemetry.readings;

import java.util.List;

/** Reponse paginee de l'API de consultation. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
