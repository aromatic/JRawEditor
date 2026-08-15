package it.aromagnoli.raw.processing;

import it.aromagnoli.raw.model.ExposureSettings;
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.IntBuffer;

public class ExposureProcessingEngine {

    /**
     * Applica Esposizione (EV) e Punto di Nero ad una Mat OpenCV (CV_32FC3)
     * e converte il risultato in una Image JavaFX.
     *
     * @param srcFloat Mat di partenza in formato CV_32FC3 (valori 0.0 - 1.0)
     * @param settings Parametri con exposureEV e blackPoint
     * @return Image JavaFX per la Preview
     */
    public static Image processAndToFxImage(Mat srcFloat, ExposureSettings settings) {
        if (srcFloat == null || srcFloat.empty()) return null;

        Mat processedFloat = new Mat();

        // 1. Applica Moltiplicatore EV (2^EV)
        double scaleFactor = Math.pow(2.0, settings.exposureEV());
        Core.multiply(srcFloat, new org.opencv.core.Scalar(scaleFactor, scaleFactor, scaleFactor), processedFloat);

        // 2. Applica Punto di Nero
        double bp = settings.blackPoint();
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
        Mat bgra8U = prepareBgraMatForJavaFX(processedFloat);
        
        // Converti la Mat 8U in JavaFX WritableImage
        Image fxImage = matToFxImage(bgra8U);

        // Cleanup mat temporanee
        processedFloat.release();
        bgra8U.release();

        return fxImage;
    }

    public static Mat prepareBgraMatForJavaFX(Mat floatMat) {
        Mat byteMat = new Mat();
        // Converti e applica lo scale * 255 (con clamp automatico a 255)
        floatMat.convertTo(byteMat, CvType.CV_8UC3, 255.0);

        Mat bgraMat = new Mat();
        // JavaFX accetta al meglio BGRA / ARGB
        Imgproc.cvtColor(byteMat, bgraMat, Imgproc.COLOR_BGR2BGRA);
        byteMat.release();

        return bgraMat;
    }

    /**
     * Conversione ad alte prestazioni da Mat BGRA a WritableImage tramite PixelBuffer.
     */
    public static Image matToFxImage(Mat bgraMat) {
        int width = bgraMat.cols();
        int height = bgraMat.rows();
        int channels = bgraMat.channels(); // 4 canali BGRA

        byte[] nativePixels = new byte[width * height * channels];
        bgraMat.get(0, 0, nativePixels);

        // Converte i byte BGRA native nel formato IntBuffer IntARGB_Pre per JavaFX
        IntBuffer intBuffer = IntBuffer.allocate(width * height);
        int[] intArray = intBuffer.array();

        for (int i = 0; i < width * height; i++) {
            int b = nativePixels[i * 4] & 0xFF;
            int g = nativePixels[i * 4 + 1] & 0xFF;
            int r = nativePixels[i * 4 + 2] & 0xFF;
            int a = nativePixels[i * 4 + 3] & 0xFF;

            // Formato ARGB int
            intArray[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(
                width, height, intBuffer, PixelFormat.getIntArgbPreInstance()
        );

        return new WritableImage(pixelBuffer);
    }
}