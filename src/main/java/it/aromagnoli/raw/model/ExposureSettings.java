package it.aromagnoli.raw.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rappresenta la modifica dell'esposizione e del punto di nero applicata
 * all'immagine.
 */
public record ExposureSettings(
        @JsonProperty("exposureEV") double exposureEV, // es. da -5.0 a +5.0 EV
        @JsonProperty("blackPoint") double blackPoint, // es. da 0.0 a 0.2 (0% - 20%)
        @JsonProperty("autoExposure") boolean autoExposure 
) {
    public static ExposureSettings defaultAutoSettings() {
        return new ExposureSettings(0.0, 0.0, true);
    }

    public static ExposureSettings defaultManualSettings() {
        return new ExposureSettings(0.0, 0.0, false);
    }
}
