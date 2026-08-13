
package it.aromagnoli.raw.ui;

import it.aromagnoli.raw.nativeffi.LibRawFFM;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;

public class RawEditorController {

    private final BorderPane rootLayout = new BorderPane();
    private final ImageView imageView = new ImageView();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label("Seleziona un file RAW per iniziare");

    // Controlli UI Generali
    private CheckBox chkFastPreview;
    private CheckBox chkCameraWB;
    private CheckBox chkAutoWB;
    private Button btnOpenFile;

    // Selector Modalità
    private RadioButton rbStandardMode;
    private RadioButton rbLinearMode;
    private ToggleGroup modeToggleGroup;

    // Controlli UI Avanzati per Modalità Lineare
    private VBox linearOptionsBox;
    private ComboBox<DemosaicOption> cmbDemosaicAlgo;
    private ComboBox<ColorSpaceOption> cmbOutputColor;
    private ComboBox<Integer> cmbOutputBps;
    
    // Custom WB Multipliers
    private CheckBox chkCustomWB;
    private Spinner<Double> spinMulR;
    private Spinner<Double> spinMulG;
    private Spinner<Double> spinMulB;
    private Spinner<Double> spinMulG2;
    private GridPane customWbGrid;

    private File currentRawFile = null;

    public RawEditorController(Stage stage) {
        setupUI(stage);
    }

    public BorderPane getRootLayout() {
        return rootLayout;
    }

    private void setupUI(Stage stage) {
        // --- Area Centrale (Immagine & Spinner) ---
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(rootLayout.widthProperty().subtract(320));
        imageView.fitHeightProperty().bind(rootLayout.heightProperty().subtract(50));

        StackPaneImageWrapper centerPane = new StackPaneImageWrapper(imageView, progressIndicator);
        progressIndicator.setVisible(false);
        rootLayout.setCenter(centerPane);

        // --- Pannello Laterale Destro (Controlli RAW) ---
        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(15));
        sidebar.setStyle("-fx-background-color: #2b2b2b;");
        sidebar.setPrefWidth(300);

        ScrollPane scrollSidebar = new ScrollPane(sidebar);
        scrollSidebar.setFitToWidth(true);
        scrollSidebar.setStyle("-fx-background-color: #2b2b2b; -fx-background: #2b2b2b;");

        Label titleLabel = new Label("Impostazioni RAW");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        btnOpenFile = new Button("📁 Apri File RAW");
        btnOpenFile.setMaxWidth(Double.MAX_VALUE);
        btnOpenFile.setOnAction(e -> openFileChooser(stage));

        // --- Selezione Modalità Processing ---
        Label modeLabel = new Label("Pipeline di Elaborazione:");
        modeLabel.setStyle("-fx-text-fill: #ddd; -fx-font-weight: bold;");

        modeToggleGroup = new ToggleGroup();
        rbStandardMode = new RadioButton("Standard (8-bit + Gamma)");
        rbStandardMode.setToggleGroup(modeToggleGroup);
        rbStandardMode.setStyle("-fx-text-fill: white;");
        rbStandardMode.setSelected(true);

        rbLinearMode = new RadioButton("Lineare Parametrizzata (Gamma 1.0)");
        rbLinearMode.setToggleGroup(modeToggleGroup);
        rbLinearMode.setStyle("-fx-text-fill: white;");

        // --- Controlli WB & Preview Condivisi ---
        chkFastPreview = new CheckBox("Anteprima Veloce (Half Size)");
        chkFastPreview.setSelected(true);
        chkFastPreview.setStyle("-fx-text-fill: white;");

        chkCameraWB = new CheckBox("Bilanciamento Fotocamera");
        chkCameraWB.setSelected(true);
        chkCameraWB.setStyle("-fx-text-fill: white;");

        chkAutoWB = new CheckBox("Auto Bilanciamento Bianco");
        chkAutoWB.setStyle("-fx-text-fill: white;");

        // --- SEZIONE OPZIONI LINEARI ---
        linearOptionsBox = new VBox(10);
        linearOptionsBox.setPadding(new Insets(10));
        linearOptionsBox.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 5;");
        linearOptionsBox.setDisable(true); // Disabilitato finché si è in modalità standard

        Label linearTitle = new Label("⚙ Parametri Lineari Avanzati");
        linearTitle.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");

        // Demosaicing Algo
        Label lblDemosaic = new Label("Algoritmo Demosaicing:");
        lblDemosaic.setStyle("-fx-text-fill: #aaa;");
        cmbDemosaicAlgo = new ComboBox<>();
        cmbDemosaicAlgo.setMaxWidth(Double.MAX_VALUE);
        cmbDemosaicAlgo.getItems().addAll(
                new DemosaicOption("0 - Bilineare (Veloce)", 0),
                new DemosaicOption("1 - VNG", 1),
                new DemosaicOption("2 - PPG", 2),
                new DemosaicOption("3 - AHD (Default dcraw)", 3),
                new DemosaicOption("4 - DCB", 4),
                new DemosaicOption("11 - DHT", 11),
                new DemosaicOption("12 - RCD (Consigliato)", 12)
        );
        cmbDemosaicAlgo.getSelectionModel().select(6); // RCD default

        // Output Color Space
        Label lblColor = new Label("Spazio Colore Primaries:");
        lblColor.setStyle("-fx-text-fill: #aaa;");
        cmbOutputColor = new ComboBox<>();
        cmbOutputColor.setMaxWidth(Double.MAX_VALUE);
        cmbOutputColor.getItems().addAll(
                new ColorSpaceOption("0 - Raw Nativo Sensore", 0),
                new ColorSpaceOption("1 - sRGB Primaries", 1),
                new ColorSpaceOption("2 - Adobe RGB Primaries", 2),
                new ColorSpaceOption("6 - ACES Linear", 6)
        );
        cmbOutputColor.getSelectionModel().select(1); // sRGB default

        // Output BPS
        Label lblBps = new Label("Profondità di Bit (BPS):");
        lblBps.setStyle("-fx-text-fill: #aaa;");
        cmbOutputBps = new ComboBox<>();
        cmbOutputBps.setMaxWidth(Double.MAX_VALUE);
        cmbOutputBps.getItems().addAll(16, 8);
        cmbOutputBps.getSelectionModel().select(Integer.valueOf(16)); // 16-bit consigliato

        // Custom WB Multipliers
        chkCustomWB = new CheckBox("Usa WB Personalizzato");
        chkCustomWB.setStyle("-fx-text-fill: white;");

        customWbGrid = new GridPane();
        customWbGrid.setHgap(5);
        customWbGrid.setVgap(5);
        customWbGrid.setDisable(true);

        spinMulR  = createWbSpinner();
        spinMulG  = createWbSpinner();
        spinMulB  = createWbSpinner();
        spinMulG2 = createWbSpinner();

        customWbGrid.add(new Label("R:") {{ setStyle("-fx-text-fill: white;"); }}, 0, 0);
        customWbGrid.add(spinMulR, 1, 0);
        customWbGrid.add(new Label("G:") {{ setStyle("-fx-text-fill: white;"); }}, 2, 0);
        customWbGrid.add(spinMulG, 3, 0);
        customWbGrid.add(new Label("B:") {{ setStyle("-fx-text-fill: white;"); }}, 0, 1);
        customWbGrid.add(spinMulB, 1, 1);
        customWbGrid.add(new Label("G2:") {{ setStyle("-fx-text-fill: white;"); }}, 2, 1);
        customWbGrid.add(spinMulG2, 3, 1);

        linearOptionsBox.getChildren().addAll(
                linearTitle,
                lblDemosaic, cmbDemosaicAlgo,
                lblColor, cmbOutputColor,
                lblBps, cmbOutputBps,
                new Separator(),
                chkCustomWB, customWbGrid
        );

        // --- LISTENER E EVENTI UI ---

        // Attivazione/Disattivazione Box opzioni lineari
        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isLinear = rbLinearMode.isSelected();
            linearOptionsBox.setDisable(!isLinear);
            reloadRawImage();
        });

        // Gestione mutua esclusione WB
        chkCameraWB.setOnAction(e -> {
            if (chkCameraWB.isSelected()) {
                chkAutoWB.setSelected(false);
                chkCustomWB.setSelected(false);
                customWbGrid.setDisable(true);
            }
            reloadRawImage();
        });

        chkAutoWB.setOnAction(e -> {
            if (chkAutoWB.isSelected()) {
                chkCameraWB.setSelected(false);
                chkCustomWB.setSelected(false);
                customWbGrid.setDisable(true);
            }
            reloadRawImage();
        });

        chkCustomWB.setOnAction(e -> {
            boolean isCustom = chkCustomWB.isSelected();
            customWbGrid.setDisable(!isCustom);
            if (isCustom) {
                chkCameraWB.setSelected(false);
                chkAutoWB.setSelected(false);
            }
            reloadRawImage();
        });

        chkFastPreview.setOnAction(e -> reloadRawImage());
        cmbDemosaicAlgo.setOnAction(e -> reloadRawImage());
        cmbOutputColor.setOnAction(e -> reloadRawImage());
        cmbOutputBps.setOnAction(e -> reloadRawImage());

        // Reload sui cambiamenti degli spinner WB
        spinMulR.valueProperty().addListener((o, oldV, newV) -> { if (chkCustomWB.isSelected()) reloadRawImage(); });
        spinMulG.valueProperty().addListener((o, oldV, newV) -> { if (chkCustomWB.isSelected()) reloadRawImage(); });
        spinMulB.valueProperty().addListener((o, oldV, newV) -> { if (chkCustomWB.isSelected()) reloadRawImage(); });
        spinMulG2.valueProperty().addListener((o, oldV, newV) -> { if (chkCustomWB.isSelected()) reloadRawImage(); });

        sidebar.getChildren().addAll(
                titleLabel,
                btnOpenFile,
                new Separator(),
                modeLabel,
                rbStandardMode,
                rbLinearMode,
                new Separator(),
                chkFastPreview,
                chkCameraWB,
                chkAutoWB,
                new Separator(),
                linearOptionsBox
        );

        rootLayout.setRight(scrollSidebar);

        // --- Barra di Stato Inferiore ---
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #1e1e1e;");
        statusLabel.setStyle("-fx-text-fill: #aaa;");
        rootLayout.setBottom(statusBar);
    }

    private Spinner<Double> createWbSpinner() {
        Spinner<Double> spinner = new Spinner<>(0.1, 10.0, 1.0, 0.05);
        spinner.setEditable(true);
        spinner.setPrefWidth(65);
        return spinner;
    }

    private void openFileChooser(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleziona File RAW");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File RAW", "*.CR2", "*.CR3", "*.NEF", "*.ARW", "*.DNG", "*.RAF", "*.ORF", "*.rw2"),
                new FileChooser.ExtensionFilter("Tutti i file", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            this.currentRawFile = selectedFile;
            reloadRawImage();
        }
    }

    private void reloadRawImage() {
        if (currentRawFile == null) return;

        progressIndicator.setVisible(true);
        statusLabel.setText("Elaborazione RAW in corso via LibRaw FFM...");
        btnOpenFile.setDisable(true);

        boolean fastPreview = chkFastPreview.isSelected();
        boolean cameraWB = chkCameraWB.isSelected();
        boolean autoWB = chkAutoWB.isSelected();
        boolean isLinearMode = rbLinearMode.isSelected();
        String filePath = currentRawFile.getAbsolutePath();

        // Parametri per modalità lineare
        int demosaicAlgo = cmbDemosaicAlgo.getValue() != null ? cmbDemosaicAlgo.getValue().code() : 12;
        int outputColor = cmbOutputColor.getValue() != null ? cmbOutputColor.getValue().code() : 1;
        int outputBps = cmbOutputBps.getValue() != null ? cmbOutputBps.getValue() : 16;

        float[] customMul = null;
        if (chkCustomWB.isSelected()) {
            customMul = new float[]{
                    spinMulR.getValue().floatValue(),
                    spinMulG.getValue().floatValue(),
                    spinMulB.getValue().floatValue(),
                    spinMulG2.getValue().floatValue()
            };
        }

        final float[] finalCustomMul = customMul;

        // Esecuzione asincrona del demosaicing in background
        Task<BufferedImage> rawTask = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                try (LibRawFFM rawDecoder = new LibRawFFM()) {
                    if (!isLinearMode) {
                        // VECCHIO METODO (Inalterato)
                        return rawDecoder.processRawFile(filePath, fastPreview, cameraWB, autoWB);
                    } else {
                        // NUOVO METODO LINEARE (Parametrizzato)
                        return rawDecoder.processRawFileLinear(
                                filePath,
                                cameraWB,
                                autoWB,
                                finalCustomMul,
                                demosaicAlgo,
                                outputColor,
                                outputBps,
                                fastPreview
                        );
                    }
                } catch (Throwable t) {
                    throw new Exception("Errore decodifica RAW: " + t.getMessage(), t);
                }
            }
        };

        rawTask.setOnSucceeded(e -> {
            BufferedImage bImg = rawTask.getValue();
            imageView.setImage(SwingFXUtils.toFXImage(bImg, null));
            progressIndicator.setVisible(false);
            btnOpenFile.setDisable(false);

            String modeStr = isLinearMode ? ("Lineare " + outputBps + "-bit") : "Standard 8-bit";
            statusLabel.setText("Caricato [" + modeStr + "]: " + currentRawFile.getName() + " (" + bImg.getWidth() + "x" + bImg.getHeight() + " px)");
        });

        rawTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            btnOpenFile.setDisable(false);
            Throwable ex = rawTask.getException();
            statusLabel.setText("Errore: " + ex.getMessage());
            ex.printStackTrace();
        });

        new Thread(rawTask).start();
    }

    // Records di comodo per popolamento ComboBox
    private record DemosaicOption(String label, int code) {
        @Override
        public String toString() { return label; }
    }

    private record ColorSpaceOption(String label, int code) {
        @Override
        public String toString() { return label; }
    }

    // Helper per centrare l'immagine e lo spinner
    private static class StackPaneImageWrapper extends javafx.scene.layout.StackPane {
        public StackPaneImageWrapper(ImageView iv, ProgressIndicator pi) {
            getChildren().addAll(iv, pi);
            setAlignment(Pos.CENTER);
            setStyle("-fx-background-color: #121212;");
        }
    }
}




/*package it.aromagnoli.raw.ui;

import it.aromagnoli.raw.nativeffi.LibRawFFM;

import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;

public class RawEditorController {

    private final BorderPane rootLayout = new BorderPane();
    private final ImageView imageView = new ImageView();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label("Seleziona un file RAW per iniziare");

    // Controlli UI
    private CheckBox chkFastPreview;
    private CheckBox chkCameraWB;
    private CheckBox chkAutoWB;
    private Button btnOpenFile;

    private File currentRawFile = null;

    public RawEditorController(Stage stage) {
        setupUI(stage);
    }

    public BorderPane getRootLayout() {
        return rootLayout;
    }

    private void setupUI(Stage stage) {
        // --- Area Centrale (Immagine & Spinner) ---
        imageView.setPreserveRatio(true);
        // Ridimensiona l'immagine mantenendo le proporzioni nella finestra
        imageView.fitWidthProperty().bind(rootLayout.widthProperty().subtract(250));
        imageView.fitHeightProperty().bind(rootLayout.heightProperty().subtract(50));

        StackPaneImageWrapper centerPane = new StackPaneImageWrapper(imageView, progressIndicator);
        progressIndicator.setVisible(false);
        rootLayout.setCenter(centerPane);

        // --- Pannello Laterale Destro (Controlli RAW) ---
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(15));
        sidebar.setStyle("-fx-background-color: #2b2b2b;");
        sidebar.setPrefWidth(240);

        Label titleLabel = new Label("Impostazioni RAW");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        btnOpenFile = new Button("📁 Apri File RAW");
        btnOpenFile.setMaxWidth(Double.MAX_VALUE);
        btnOpenFile.setOnAction(e -> openFileChooser(stage));

        chkFastPreview = new CheckBox("Anteprima Veloce (Half Size)");
        chkFastPreview.setSelected(true);
        chkFastPreview.setStyle("-fx-text-fill: white;");

        chkCameraWB = new CheckBox("Bilanciamento Fotocamera");
        chkCameraWB.setSelected(true);
        chkCameraWB.setStyle("-fx-text-fill: white;");

        chkAutoWB = new CheckBox("Auto Bilanciamento Bianco");
        chkAutoWB.setStyle("-fx-text-fill: white;");

        // Mutua esclusione basica per i WB
        chkCameraWB.setOnAction(e -> {
            if (chkCameraWB.isSelected()) chkAutoWB.setSelected(false);
            reloadRawImage();
        });
        chkAutoWB.setOnAction(e -> {
            if (chkAutoWB.isSelected()) chkCameraWB.setSelected(false);
            reloadRawImage();
        });
        chkFastPreview.setOnAction(e -> reloadRawImage());

        sidebar.getChildren().addAll(
                titleLabel,
                btnOpenFile,
                new Separator(),
                chkFastPreview,
                chkCameraWB,
                chkAutoWB
        );

        rootLayout.setRight(sidebar);

        // --- Barra di Stato Inferiore ---
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #1e1e1e;");
        statusLabel.setStyle("-fx-text-fill: #aaa;");
        rootLayout.setBottom(statusBar);
    }

    private void openFileChooser(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleziona File RAW");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File RAW", "*.CR2", "*.CR3", "*.NEF", "*.ARW", "*.DNG", "*.RAF", "*.ORF", "*.rw2"),
                new FileChooser.ExtensionFilter("Tutti i file", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            this.currentRawFile = selectedFile;
            reloadRawImage();
        }
    }

    private void reloadRawImage() {
        if (currentRawFile == null) return;

        progressIndicator.setVisible(true);
        statusLabel.setText("Elaborazione RAW in corso via LibRaw FFM...");
        btnOpenFile.setDisable(true);

        boolean fastPreview = chkFastPreview.isSelected();
        boolean cameraWB = chkCameraWB.isSelected();
        boolean autoWB = chkAutoWB.isSelected();
        String filePath = currentRawFile.getAbsolutePath();

        // Esecuzione asincrona del demosaicing in background
        Task<BufferedImage> rawTask = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                try (LibRawFFM rawDecoder = new LibRawFFM()) {
                    return rawDecoder.processRawFile(filePath, fastPreview, cameraWB, autoWB);
                } catch (Throwable t) {
                    throw new Exception("Errore decodifica RAW: " + t.getMessage(), t);
                }
            }
        };

        rawTask.setOnSucceeded(e -> {
            BufferedImage bImg = rawTask.getValue();
            imageView.setImage(SwingFXUtils.toFXImage(bImg, null));
            progressIndicator.setVisible(false);
            btnOpenFile.setDisable(false);
            statusLabel.setText("Caricato: " + currentRawFile.getName() + " (" + bImg.getWidth() + "x" + bImg.getHeight() + " px)");
        });

        rawTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            btnOpenFile.setDisable(false);
            Throwable ex = rawTask.getException();
            statusLabel.setText("Errore: " + ex.getMessage());
            ex.printStackTrace();
        });

        new Thread(rawTask).start();
    }

    // Helper per centrare l'immagine e lo spinner
    private static class StackPaneImageWrapper extends javafx.scene.layout.StackPane {
        public StackPaneImageWrapper(ImageView iv, ProgressIndicator pi) {
            getChildren().addAll(iv, pi);
            setAlignment(Pos.CENTER);
            setStyle("-fx-background-color: #121212;");
        }
    }
}
*/