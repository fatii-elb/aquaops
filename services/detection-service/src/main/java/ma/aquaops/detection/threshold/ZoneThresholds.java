package ma.aquaops.detection.threshold;

/**
 * Seuils de detection d'une zone (FR-13).
 * <ul>
 *   <li>fuite (FR-09) : pression sous {@code leakMinPressure} (bar) pendant {@code leakDurationSeconds} secondes
 *       ET debit au-dessus de {@code leakMaxFlow} ;</li>
 *   <li>capteur silencieux (FR-10) : aucune mesure depuis {@code silenceThresholdSeconds} secondes ;</li>
 *   <li>niveau de reservoir bas (FR-12) : niveau strictement sous {@code tankMinLevel} (metres).</li>
 * </ul>
 */
public record ZoneThresholds(
        double leakMinPressure,
        double leakMaxFlow,
        long leakDurationSeconds,
        long silenceThresholdSeconds,
        double tankMinLevel) {
}
