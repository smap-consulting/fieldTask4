package org.odk.collect.android.geo;

/*
 * This class was used for Mapbox but has been disabled as this version of Mapbox does not meet google page size requirements
 */

class MapboxMapConfigurator {
    private final String prefKey;
    private final int sourceLabelId;
    private final MapboxUrlOption[] options;

    /** Constructs a configurator with a few Mapbox style URL options to choose from. */
    MapboxMapConfigurator(String prefKey, int sourceLabelId, MapboxUrlOption... options) {
        this.prefKey = prefKey;
        this.sourceLabelId = sourceLabelId;
        this.options = options;
    }

    static class MapboxUrlOption {
        final String url;
        final int labelId;

        MapboxUrlOption(String url, int labelId) {
            this.url = url;
            this.labelId = labelId;
        }
    }
}
