package ma.aquaops.telemetry.validation;

import java.util.List;

public record ValidationResult(List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }
}