package it.aromagnoli.raw.ui;

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
