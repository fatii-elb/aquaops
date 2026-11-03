package ma.aquaops.simulator.scenario;

/**
 * Ce qu'un capteur emet a un instant donne.
 *
 * @param emit      false si le capteur se tait (scenario silence)
 * @param value     valeur chiffree ; null pour PUMP_STATE ou en silence
 * @param pumpState "NORMAL" ou "FAULT" pour PUMP_STATE, sinon null
 */
public record Sample(boolean emit, Double value, String pumpState) {

    public static Sample silence() {
        return new Sample(false, null, null);
    }

    public static Sample value(double value) {
        return new Sample(true, value, null);
    }

    public static Sample pump(String pumpState) {
        return new Sample(true, null, pumpState);
    }
}
