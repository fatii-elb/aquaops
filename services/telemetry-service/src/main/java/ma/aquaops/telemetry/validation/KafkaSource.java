package ma.aquaops.telemetry.validation;

/**
 * Origine Kafka d'une mesure : partition, offset et horodatage du message.
 * Deux livraisons du meme message ont la meme origine : c'est ce qui permet d'ignorer un doublon.
 * L'horodatage evite de confondre deux messages si le topic est recree (les offsets repartent de 0).
 *
 * readingId (RC-04/RC-05) : identifiant de la mesure choisi par l'emetteur (en-tete HTTP
 * Idempotency-Key, recopie dans l'en-tete Kafka reading-id). Il identifie la MESURE, pas le
 * message : si l'emetteur renvoie une mesure (reponse perdue), Kafka recoit deux messages
 * differents, mais la base n'enregistre qu'une ligne. Null si l'emetteur n'en donne pas.
 */
public record KafkaSource(int partition, long offset, long timestamp, String readingId) {

    /** En-tete Kafka qui porte l'identifiant de la mesure (le JSON du contrat v1 n'est pas modifie). */
    public static final String READING_ID_HEADER = "reading-id";

    private static final java.util.regex.Pattern UUID =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public KafkaSource(int partition, long offset, long timestamp) {
        this(partition, offset, timestamp, null);
    }

    /** Vrai si la valeur est un UUID (36 caracteres) : seule forme acceptee pour readingId. */
    public static boolean isReadingId(String value) {
        return value != null && UUID.matcher(value).matches();
    }
}
