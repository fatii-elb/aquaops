package ma.aquaops.telemetry.readings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class TelemetryIngestControllerTests {

    private static final String VALID_MEASURE = """
            {"eventVersion":"1","sensorId":42,"zoneId":7,"type":"PRESSURE",
             "value":2.4,"unit":"bar","measuredAt":"2026-10-20T10:00:00Z"}
            """;

    private KafkaTemplate<String, String> kafka;
    private TelemetryIngestController controller;

    @BeforeEach
    void setUp() {
        kafka = mock();
        controller = new TelemetryIngestController(kafka);
    }

    @Test
    void validePuisPublieEtAccepteLaMesure() {
        when(kafka.send(eq("sensor-readings"), eq("42"), eq(VALID_MEASURE)))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        var response = controller.ingest(VALID_MEASURE).join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(kafka).send("sensor-readings", "42", VALID_MEASURE);
    }

    @Test
    void mesureInvalideEstRejeteeSansPublication() {
        String invalidMeasure = """
                {"eventVersion":"1","sensorId":0,"zoneId":7,"type":"PRESSURE",
                 "value":2.4,"measuredAt":"2026-10-20T10:00:00Z"}
                """;

        var response = controller.ingest(invalidMeasure).join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(kafka);
    }

    @Test
    void jsonIllisibleEstRejeteSansPublication() {
        var response = controller.ingest("{").join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(kafka);
    }

    @Test
    void panneKafkaNeRetournePasUnAcquittementDeSucces() {
        when(kafka.send(eq("sensor-readings"), eq("42"), eq(VALID_MEASURE)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        var response = controller.ingest(VALID_MEASURE).join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // RC-04 / RC-05 : identifiant de la mesure, hors du JSON (le contrat v1 interdit les champs en plus).
    private static final String READING_ID = "00000000-0000-4000-8000-000000000042";

    @Test
    @SuppressWarnings("unchecked")
    void idempotencyKeyRecopieeDansUnEnTeteKafkaSansToucherAuJson() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        var response = controller.ingest(VALID_MEASURE, READING_ID).join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        ProducerRecord<String, String> record = sent.getValue();
        assertThat(record.topic()).isEqualTo("sensor-readings");
        assertThat(record.key()).isEqualTo("42");
        assertThat(record.value()).isEqualTo(VALID_MEASURE);
        assertThat(new String(record.headers().lastHeader("reading-id").value(), StandardCharsets.UTF_8)).isEqualTo(READING_ID);
    }

    @Test
    void idempotencyKeyMalFormeeEstRejeteeSansPublication() {
        var response = controller.ingest(VALID_MEASURE, "pas-un-uuid").join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(kafka);
    }

    @Test
    @SuppressWarnings("unchecked")
    void avecIdempotencyKeyUnePanneKafkaNestPasAcquittee() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        var response = controller.ingest(VALID_MEASURE, READING_ID).join();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
