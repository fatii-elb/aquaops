package ma.aquaops.telemetry.readings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
