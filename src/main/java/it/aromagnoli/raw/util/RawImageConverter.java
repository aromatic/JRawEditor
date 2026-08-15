package it.aromagnoli.raw.util;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;

public class RawImageConverter {

    /**
     * Converte un BufferedImage (8-bit o 16-bit per canale) in una Mat OpenCV CV_32FC3 [0.0 - 1.0]
     */
    public static Mat bufferedImageToMatFloat32(BufferedImage bImg) {
        int width = bImg.getWidth();
        int height = bImg.getHeight();
        Mat floatMat = new Mat();

        // Controllo se il buffer di campionamento è a 16-bit (DataBuffer.TYPE_USHORT)
        boolean is16Bit = bImg.getSampleModel().getTransferType() == DataBuffer.TYPE_USHORT;

        if (is16Bit) {
            // === DATI A 16-BIT PER CANALE (es. decodifica LibRaw Lineare 16-bit) ===
            short[] data = ((DataBufferUShort) bImg.getRaster().getDataBuffer()).getData();
            
            Mat mat16U = new Mat(height, width, CvType.CV_16UC3);
            mat16U.put(0, 0, data);

            // Se il BufferedImage è RGB, converti nel formato BGR nativo di OpenCV
            Mat mat16UBgr = new Mat();
            Imgproc.cvtColor(mat16U, mat16UBgr, Imgproc.COLOR_RGB2BGR);
            mat16U.release();

            // Normalizza i valori da [0, 65535] a [0.0, 1.0] in Float32
            mat16UBgr.convertTo(floatMat, CvType.CV_32FC3, 1.0 / 65535.0);
            mat16UBgr.release();

        } else {
            // === DATI A 8-BIT PER CANALE (es. TYPE_3BYTE_BGR o TYPE_INT_RGB) ===
            Mat mat8U;
            
            if (bImg.getType() == BufferedImage.TYPE_3BYTE_BGR) {
                byte[] data = ((DataBufferByte) bImg.getRaster().getDataBuffer()).getData();
                mat8U = new Mat(height, width, CvType.CV_8UC3);
                mat8U.put(0, 0, data);
            } else {
                // Fallback generico per altri tipi di BufferedImage a 8-bit
                byte[] data = new byte[width * height * 3];
                int idx = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = bImg.getRGB(x, y);
                        data[idx++] = (byte) (rgb & 0xFF);         // Blue
                        data[idx++] = (byte) ((rgb >> 8) & 0xFF);  // Green
                        data[idx++] = (byte) ((rgb >> 16) & 0xFF); // Red
                    }
                }
                mat8U = new Mat(height, width, CvType.CV_8UC3);
                mat8U.put(0, 0, data);
            }

            // Normalizza i valori da [0, 255] a [0.0, 1.0] in Float32
            mat8U.convertTo(floatMat, CvType.CV_32FC3, 1.0 / 255.0);
            mat8U.release();
        }

        return floatMat;
    }
}