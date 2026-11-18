package ma.aquaops.telemetry.validation;

/**
 * Origine Kafka d'une mesure : partition, offset et horodatage du message.
 * Deux livraisons du meme message ont la meme origine : c'est ce qui permet d'ignorer un doublon.
 * L'horodatage evite de confondre deux messages si le topic est recree (les offsets repartent de 0).
 */
public record KafkaSource(int partition, long offset, long timestamp) {
}
