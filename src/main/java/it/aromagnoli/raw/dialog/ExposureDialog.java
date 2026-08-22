package it.aromagnoli.raw.dialog;

import it.aromagnoli.raw.model.ExposureSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.function.Consumer;

public class ExposureDialog extends Dialog<ExposureSettings> {

    private final Slider exposureSlider;
    private final Slider blackPointSlider;
    private final Label exposureValueLabel;
    private final Label blackPointValueLabel;
    private boolean autoExposure;

    public ExposureDialog(ExposureSettings initialSettings, Consumer<ExposureSettings> onPreviewUpdate) {
        this.autoExposure = initialSettings.autoExposure();
        setTitle("Regolazione Esposizione " + (initialSettings.autoExposure() ? "Automatica" : "Manuale"));
        setHeaderText("Aggiusta l'esposizione (EV) e il punto di nero");

        // Tipi di Bottoni
        ButtonType confirmButtonType = new ButtonType("Conferma", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(confirmButtonType, cancelButtonType);

        // Grid Layout
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 20, 20));

        // 1. Slider Esposizione (in EV: -5.0 a +5.0 EV)
        Label exposureLabel = new Label("Esposizione (EV):");
        exposureSlider = new Slider(-1.0, 4.0, initialSettings.exposureEV());
        exposureSlider.setMajorTickUnit(1.0);
        exposureSlider.setMinorTickCount(3);
        exposureSlider.setBlockIncrement(0.01);
        exposureSlider.setShowTickMarks(true);
        exposureSlider.setShowTickLabels(true);
        exposureSlider.setPrefWidth(250);

        exposureValueLabel = new Label(String.format("%+.2f EV", initialSettings.exposureEV()));

        // Format label dinamica
        exposureSlider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double val) {
                return String.format("%+.0f", val);
            }
            @Override
            public Double fromString(String string) { return 0.0; }
        });

        // 2. Slider Punto di Nero (da 0.0 a 0.2, ovvero 0% - 20%)
        Label blackPointLabel = new Label("Punto di Nero:");
        blackPointSlider = new Slider(0.0, 0.20, initialSettings.blackPoint());
        blackPointSlider.setMajorTickUnit(0.05);
        blackPointSlider.setMinorTickCount(4);
        blackPointSlider.setShowTickMarks(true);
        blackPointSlider.setShowTickLabels(false);
        blackPointSlider.setPrefWidth(250);

        blackPointValueLabel = new Label(String.format("%.1f%%", initialSettings.blackPoint() * 100));

        // Posizionamento nel GridPane
        grid.add(exposureLabel, 0, 0);
        grid.add(exposureSlider, 1, 0);
        grid.add(exposureValueLabel, 2, 0);

        grid.add(blackPointLabel, 0, 1);
        grid.add(blackPointSlider, 1, 1);
        grid.add(blackPointValueLabel, 2, 1);

        getDialogPane().setContent(grid);

        // Listener per il Live Preview
        Runnable updateHandler = () -> {
            double ev = exposureSlider.getValue();
            double bp = blackPointSlider.getValue();

            exposureValueLabel.setText(String.format("%+.2f EV", ev));
            blackPointValueLabel.setText(String.format("%.1f%%", bp * 100));

            // Notifica il controller per il rendering non distruttivo in tempo reale
            if (onPreviewUpdate != null) {
                onPreviewUpdate.accept(new ExposureSettings(ev, bp, this.autoExposure));
            }
        };

        exposureSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateHandler.run());
        blackPointSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateHandler.run());

        // Gestione dello scroll della rotella del mouse sullo Slider
        exposureSlider.setOnScroll(event -> {
            // Determina la direzione del delta dello scroll
            double delta = event.getDeltaY();
            
            if (delta != 0) {
                // Calcola il passo di incremento/decremento.
                // Puoi usare slider.getBlockIncrement() oppure un valore personalizzato (es. 0.1)
                double step = exposureSlider.getBlockIncrement();
                
                if (delta > 0) {
                    // Scroll verso l'alto: incrementa il valore entro il massimo
                    exposureSlider.setValue(Math.min(exposureSlider.getMax(), exposureSlider.getValue() + step));
                } else {
                    // Scroll verso il basso: decrementa il valore entro il minimo
                    exposureSlider.setValue(Math.max(exposureSlider.getMin(), exposureSlider.getValue() - step));
                }
                
                // Consuma l'evento per evitare che risalga a un eventuale ScrollPane genitore
                event.consume();
            }
        });
        // Result Converter
        setResultConverter(dialogButton -> {
            if (dialogButton == confirmButtonType) {
                return new ExposureSettings(
                    exposureSlider.getValue(),
                    blackPointSlider.getValue(),
                    this.autoExposure
                );
            }
            return null; // Annulla ritorna null
        });
    }
}
