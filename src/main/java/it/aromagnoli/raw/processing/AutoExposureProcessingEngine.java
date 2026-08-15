package it.aromagnoli.raw.processing;

import it.aromagnoli.raw.model.ExposureSettings;
import javafx.scene.image.Image;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class AutoExposureProcessingEngine {

    private static final double TARGET_MID_GRAY = 0.18; // 18% grigio medio standard
    private static final double LOG_2 = Math.log(2.0);  // Costante per log2 in Java

 // =========================================================================
    // 1. STIMA AUTOMATICA DELL'ESPOSIZIONE (Basata sul Grigio Medio 18%)
    // =========================================================================

    /**
     * Calcola la correzione di esposizione automatica (in EV/stop)
     * basata sulla media geometrica della luminanza della Mat OpenCV.
     *
     * @param srcFloat Mat 32-bit floating point a 3 canali (RGB/BGR)
     */
    public static double calculateAutoExposure(Mat srcFloat) {
        Mat lumMat = new Mat();
        Mat weights = new Mat(1, 3, CvType.CV_32FC1);
        weights.put(0, 0, 0.2126, 0.7152, 0.0722); // Pesi luminanza Rec.709 (RGB)

        // Calcola la luminanza scalare per ogni pixel
        Core.transform(srcFloat, lumMat, weights);

        // Clampa a un minimo per evitare log(0)
        Mat clampedLum = new Mat();
        Imgproc.threshold(lumMat, clampedLum, 0.0001, 1.0, Imgproc.THRESH_TOZERO);

        // Logaritmo naturale della luminanza
        Mat logLum = new Mat();
        Core.log(clampedLum, logLum);

        // Calcolo della media del logaritmo
        Scalar meanLog = Core.mean(logLum);
        double geometricMeanLuminance = Math.exp(meanLog.val[0]);

        // Cleanup memoria nativa
        lumMat.release();
        weights.release();
        clampedLum.release();
        logLum.release();

        if (geometricMeanLuminance <= 0.0001) return 0.0;

        // log2(TARGET_MID_GRAY / geometricMeanLuminance) via cambio di base
        return Math.log(TARGET_MID_GRAY / geometricMeanLuminance) / LOG_2;
    }

    // =========================================================================
    // 2. APPLICAZIONE DI ESPOSIZIONE E PUNTO DI NERO (Vettorializzata)
    // =========================================================================

    /**
     * Applica esposizione e punto di nero direttamente sulla Mat float.
     */
    public static Image applyExposureAndBlackPoint(Mat srcFloat, double evShift, double blackPoint) {
        Mat processedFloat = new Mat();

        // 1. Applica Moltiplicatore EV (2^EV)
        double scaleFactor = Math.pow(2.0, evShift);
        Core.multiply(srcFloat, new org.opencv.core.Scalar(scaleFactor, scaleFactor, scaleFactor), processedFloat);

        // 2. Applica Punto di Nero
        double bp = blackPoint;
        if (bp > 0.0) {
            // Sottrae il valore del punto di nero
            Core.subtract(processedFloat, new org.opencv.core.Scalar(bp, bp, bp), processedFloat);

            // Clip dei valori negativi a 0.0
            Core.max(processedFloat, new org.opencv.core.Scalar(0.0, 0.0, 0.0), processedFloat);

            // Riscala l'intervallo rimanente [0, 1 - bp] a [0, 1]
            double rangeScale = 1.0 / Math.max(0.0001, (1.0 - bp));
            Core.multiply(processedFloat, new org.opencv.core.Scalar(rangeScale, rangeScale, rangeScale), processedFloat);
        }

        // 3. Conversione da Float [0.0, 1.0] a 8-bit BGRA [0, 255] per JavaFX
        Mat bgra8U = ExposureProcessingEngine.prepareBgraMatForJavaFX(processedFloat);
        
        // Converti la Mat 8U in JavaFX WritableImage
        Image fxImage = ExposureProcessingEngine.matToFxImage(bgra8U);

        // Cleanup mat temporanee
        processedFloat.release();
        bgra8U.release();

        return fxImage;
    }

    // =========================================================================
    // 3. PIPELINE COMPLETA E CONVERSIONE IN JAVAFX IMAGE
    // =========================================================================

    /**
     * Elabora la Mat float lineare e la converte in un'immagine JavaFX (Image).
     * 
     * Comportamento Esposizione:
     * - Se settings.autoExposure == true OPPURE settings.evShift == 0.0,
     *   calcola prima l'esposizione automatica al 18% di grigio medio.
     * - Se settings.evShift != 0.0, compensa il risultato aggiungendo/sottraendo 
     *   gli EV specificati dall'utente.
     *
     * @param srcFloat Mat con dati float 32-bit (CV_32FC3) in formato RGB lineare.
     * @param settings Parametri di esposizione e punto di nero.
     * @return Immagine JavaFX (Image) pronta da visualizzare.
     */
    public static Image processAndToFxImage(Mat srcFloat, ExposureSettings settings) {
        // Lavoriamo su una copia per non alterare la Mat sorgente originale
        Mat processed = srcFloat.clone();

        double finalEvToApply = 0.0;

        // Se l'utente ha richiesto espressamente l'Auto Exposure o se evShift è 0,
        // calcoliamo la base automatica al 18%
        if (settings.autoExposure() || settings.exposureEV() == 0.0) {
            finalEvToApply = calculateAutoExposure(processed);
            System.out.println("AUTOESPOSIZIONE: " + finalEvToApply);
            
            // Se autoExposure è true ED è stato specificato anche un evShift != 0,
            // usiamo evShift come offset/compensazione sopra l'auto exposure
            if (settings.autoExposure()) {
                finalEvToApply += settings.exposureEV();
                System.out.println("AUTOESPOSIZIONE COMPENSATA: " + finalEvToApply);
            }
        } else {
            // L'utente ha specificato un valore manuale e non ha impostato autoExposure
            finalEvToApply = settings.exposureEV();
            System.out.println("ESPOSIZIONE: " + finalEvToApply);
        }

        // 1. Applica l'esposizione calcolata e il punto di nero
        return applyExposureAndBlackPoint(processed, finalEvToApply, settings.blackPoint());


    }


}