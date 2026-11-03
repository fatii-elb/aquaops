package ma.aquaops.simulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires purs des scenarios de panne (SCRUM-28 / FR-06).
 * Ni Spring, ni Kafka, ni base : un scenario est une fonction du temps.
 */
class ScenarioProfilesTests {

    private static final Duration AVANT_5S = Duration.ofSeconds(4);
    private static final Duration APRES_5S = Duration.ofSeconds(6);
    private static final Duration AVANT_10S = Duration.ofSeconds(9);
    private static final Duration APRES_10S = Duration.ofSeconds(11);

    @Test
    void laFuiteBaisseLaPressionEtMonteLeDebitSeulementApres10s() {
        // Préparer
        ScenarioProfile fuite = new LeakProfile();

        // Agir
        Sample pressionAvant = fuite.sampleAt(ReadingType.PRESSURE, AVANT_10S);
        Sample debitAvant = fuite.sampleAt(ReadingType.FLOW, AVANT_10S);
        Sample pressionApres = fuite.sampleAt(ReadingType.PRESSURE, APRES_10S);
        Sample debitApres = fuite.sampleAt(ReadingType.FLOW, APRES_10S);

        // Vérifier
        assertEquals(NormalProfile.PRESSION_NORMALE, pressionAvant.value());
        assertEquals(NormalProfile.DEBIT_NORMAL, debitAvant.value());
        assertEquals(1.2, pressionApres.value());
        assertEquals(60.0, debitApres.value());
    }

    @Test
    void laPompePasseAFaultApres5sEtLeResteNeBougePas() {
        // Préparer
        ScenarioProfile pompe = new PumpFaultProfile();

        // Agir
        Sample pompeAvant = pompe.sampleAt(ReadingType.PUMP_STATE, AVANT_5S);
        Sample pompeApres = pompe.sampleAt(ReadingType.PUMP_STATE, APRES_5S);

        // Vérifier
        assertEquals("NORMAL", pompeAvant.pumpState());
        assertEquals("FAULT", pompeApres.pumpState());
        assertEquals(NormalProfile.PRESSION_NORMALE, pompe.sampleAt(ReadingType.PRESSURE, APRES_5S).value());
        assertEquals(NormalProfile.DEBIT_NORMAL, pompe.sampleAt(ReadingType.FLOW, APRES_5S).value());
        assertEquals(NormalProfile.NIVEAU_NORMAL, pompe.sampleAt(ReadingType.LEVEL, APRES_5S).value());
    }

    @Test
    void leSilenceCesseDEmettreApres5sEtLesAutresCapteursContinuent() {
        // Préparer
        ScenarioProfile silence = new SensorSilentProfile();

        // Agir
        Sample pressionAvant = silence.sampleAt(ReadingType.PRESSURE, AVANT_5S);
        Sample pressionApres = silence.sampleAt(ReadingType.PRESSURE, APRES_5S);

        // Vérifier
        assertTrue(pressionAvant.emit());
        assertFalse(pressionApres.emit());
        assertTrue(silence.sampleAt(ReadingType.FLOW, APRES_5S).emit());
        assertTrue(silence.sampleAt(ReadingType.LEVEL, APRES_5S).emit());
        assertTrue(silence.sampleAt(ReadingType.PUMP_STATE, APRES_5S).emit());
    }

    @Test
    void lePicMonteLeDebitMaisLaisseLaPressionNormale() {
        // Préparer
        ScenarioProfile pic = new LoadPeakProfile();

        // Agir
        Sample debitApres = pic.sampleAt(ReadingType.FLOW, APRES_10S);
        Sample pressionApres = pic.sampleAt(ReadingType.PRESSURE, APRES_10S);

        // Vérifier : debit haut SANS pression basse, donc aucune fuite a detecter
        assertEquals(70.0, debitApres.value());
        assertEquals(NormalProfile.PRESSION_NORMALE, pressionApres.value());
    }
}
