package it.aromagnoli.raw.nativeffi;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public class LibRawFFM implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle rawInitMH;
    private static final MethodHandle rawOpenAndUnpackMH;
    private static final MethodHandle rawProcessImageMH;
    private static final MethodHandle rawProcessLinearMH;
    private static final MethodHandle rawGetProcessedImageMH;
    private static final MethodHandle rawGetWidthMH;
    private static final MethodHandle rawGetHeightMH;
    private static final MethodHandle rawGetDataSizeMH;
    private static final MethodHandle rawGetDataPtrMH;
    private static final MethodHandle rawFreeMemImageMH;
    private static final MethodHandle rawCloseMH;

    static {
        System.loadLibrary("raw_wrapper");
        LOOKUP = SymbolLookup.loaderLookup();

        rawInitMH = LINKER.downcallHandle(
                LOOKUP.find("raw_init").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS)
        );

        rawOpenAndUnpackMH = LINKER.downcallHandle(
                LOOKUP.find("raw_open_and_unpack").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        rawProcessImageMH = LINKER.downcallHandle(
                LOOKUP.find("raw_process_image").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );

        rawProcessLinearMH = LINKER.downcallHandle(
            LOOKUP.find("raw_process_linear").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // Return type
                    ValueLayout.ADDRESS,  // handle
                    ValueLayout.JAVA_INT, // use_camera_wb
                    ValueLayout.JAVA_INT, // use_auto_wb
                    ValueLayout.ADDRESS,  // user_mul (float* array o NULL)
                    ValueLayout.JAVA_INT, // demosaic_algo
                    ValueLayout.JAVA_INT, // output_color
                    ValueLayout.JAVA_INT, // output_bps
                    ValueLayout.JAVA_INT  // half_size
            )
        );

        rawGetProcessedImageMH = LINKER.downcallHandle(
                LOOKUP.find("raw_get_processed_image").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // Binding dei nuovi getter
        rawGetWidthMH = LINKER.downcallHandle(
                LOOKUP.find("raw_get_width").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS)
        );

        rawGetHeightMH = LINKER.downcallHandle(
                LOOKUP.find("raw_get_height").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS)
        );

        rawGetDataSizeMH = LINKER.downcallHandle(
                LOOKUP.find("raw_get_data_size").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        );

        rawGetDataPtrMH = LINKER.downcallHandle(
                LOOKUP.find("raw_get_data_ptr").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        rawFreeMemImageMH = LINKER.downcallHandle(
                LOOKUP.find("raw_free_mem_image").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        rawCloseMH = LINKER.downcallHandle(
                LOOKUP.find("raw_close").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
    }

    private final MemorySegment rawHandle;

    public LibRawFFM() throws Throwable {
        this.rawHandle = (MemorySegment) rawInitMH.invokeExact();
    }

    public BufferedImage processRawFile(String rawPath, boolean fastPreview, boolean useCameraWB, boolean autoWB) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(rawPath);

            // 1. Apri e unpack
            int status = (int) rawOpenAndUnpackMH.invokeExact(rawHandle, cPath);
            if (status != 0) {
                throw new RuntimeException("Impossibile aprire il file RAW (Codice errore LibRaw: " + status + ")");
            }

            // 2. Demosaicing
            status = (int) rawProcessImageMH.invokeExact(rawHandle, fastPreview ? 1 : 0, useCameraWB ? 1 : 0, autoWB ? 1 : 0);
            if (status != 0) {
                throw new RuntimeException("Errore durante la decodifica RAW (Codice errore LibRaw: " + status + ")");
            }

            // 3. Estrazione Immagine
            MemorySegment imgProcessedPtr = (MemorySegment) rawGetProcessedImageMH.invokeExact(rawHandle);
            if (imgProcessedPtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("raw_get_processed_image ha restituito un puntatore NULL.");
            }

            try {
                // Interroghiamo direttamente C++ per i metadati
                short width = (short) rawGetWidthMH.invokeExact(imgProcessedPtr);
                short height = (short) rawGetHeightMH.invokeExact(imgProcessedPtr);
                int dataSize = (int) rawGetDataSizeMH.invokeExact(imgProcessedPtr);
                MemorySegment dataPtr = (MemorySegment) rawGetDataPtrMH.invokeExact(imgProcessedPtr);

                if (width <= 0 || height <= 0) {
                    throw new RuntimeException("Dimensioni immagine non valide lette da LibRaw: " + width + "x" + height);
                }

                // Leggiamo l'array di pixel
                MemorySegment pixelSegment = dataPtr.reinterpret(dataSize);
                byte[] rgbPixels = pixelSegment.toArray(ValueLayout.JAVA_BYTE);

                // Creazione BufferedImage
                BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                byte[] targetPixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

                // Conversione RGB -> BGR
                for (int i = 0; i < width * height; i++) {
                    int idx = i * 3;
                    targetPixels[idx]     = rgbPixels[idx + 2]; // Blue
                    targetPixels[idx + 1] = rgbPixels[idx + 1]; // Green
                    targetPixels[idx + 2] = rgbPixels[idx];     // Red
                }

                return bufferedImage;

            } finally {
                rawFreeMemImageMH.invokeExact(imgProcessedPtr);
            }
        }
    }


    /**
     * Elabora il file RAW con output lineare a 16-bit per canale (RGB).
     *
     * @param rawPath      Percorso del file RAW.
     * @param useCameraWB  true per usare il WB EXIF della fotocamera.
     * @param autoWB       true per usare il WB automatico basato sulla scena.
     * @param userMul      Array di 4 float {R, G, B, G2} per WB manuale (o null se non usato).
     * @param demosaicAlgo Algoritmo di demosaicing (es. 0=Bilineare, 3=AHD, 12=RCD).
     * @param outputColor  Spazio colore (0=Raw Nativo, 1=sRGB Primaries, 6=ACES Linear).
     * @param outputBps    Profondità di bit (16 consigliato per dati lineari, 8 per preview rapida).
     * @param fastPreview  true per mezza risoluzione (half_size).
     * @return BufferedImage a 16-bit RGB con dati strettamente lineari.
     */
    public BufferedImage processRawFileLinear(String rawPath, 
                                            boolean useCameraWB, 
                                            boolean autoWB, 
                                            float[] userMul, 
                                            int demosaicAlgo, 
                                            int outputColor, 
                                            int outputBps, 
                                            boolean fastPreview) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cPath = arena.allocateFrom(rawPath);

            // 1. Apri e unpack
            int status = (int) rawOpenAndUnpackMH.invokeExact(rawHandle, cPath);
            if (status != 0) {
                throw new RuntimeException("Impossibile aprire il file RAW (Codice errore LibRaw: " + status + ")");
            }

            // Gestione dell'array di moltiplicatori WB se passato
            MemorySegment userMulSegment = MemorySegment.NULL;
            if (userMul != null && userMul.length >= 4) {
                userMulSegment = arena.allocate(ValueLayout.JAVA_FLOAT, 4);
                for (int i = 0; i < 4; i++) {
                    userMulSegment.setAtIndex(ValueLayout.JAVA_FLOAT, i, userMul[i]);
                }
            }

            // 2. Demosaicing Lineare
            status = (int) rawProcessLinearMH.invokeExact(
                    rawHandle,
                    useCameraWB ? 1 : 0,
                    autoWB ? 1 : 0,
                    userMulSegment,
                    demosaicAlgo,
                    outputColor,
                    outputBps,
                    fastPreview ? 1 : 0
            );

            if (status != 0) {
                throw new RuntimeException("Errore durante l'elaborazione lineare RAW (Codice errore LibRaw: " + status + ")");
            }

            // 3. Estrazione Immagine
            MemorySegment imgProcessedPtr = (MemorySegment) rawGetProcessedImageMH.invokeExact(rawHandle);
            if (imgProcessedPtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("raw_get_processed_image ha restituito un puntatore NULL.");
            }

            try {
                short width = (short) rawGetWidthMH.invokeExact(imgProcessedPtr);
                short height = (short) rawGetHeightMH.invokeExact(imgProcessedPtr);
                int dataSize = (int) rawGetDataSizeMH.invokeExact(imgProcessedPtr);
                MemorySegment dataPtr = (MemorySegment) rawGetDataPtrMH.invokeExact(imgProcessedPtr);

                if (width <= 0 || height <= 0) {
                    throw new RuntimeException("Dimensioni immagine non valide lette da LibRaw: " + width + "x" + height);
                }

                MemorySegment pixelSegment = dataPtr.reinterpret(dataSize);

                // --- RAMO 16-BIT (Standard consigliato per dati lineari) ---
                if (outputBps == 16) {
                    // Leggiamo il buffer C++ come array di ushort (JAVA_SHORT in FFM)
                    short[] rgbPixels = pixelSegment.toArray(ValueLayout.JAVA_SHORT);

                    // Creiamo una BufferedImage 16-bit RGB nativa in Java AWT
                    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    int[] bits = {16, 16, 16};
                    ComponentColorModel colorModel = new ComponentColorModel(
                            cs, bits, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);

                    WritableRaster raster = Raster.createInterleavedRaster(
                            DataBuffer.TYPE_USHORT, width, height, width * 3, 3, new int[]{0, 1, 2}, null);

                    BufferedImage bufferedImage = new BufferedImage(colorModel, raster, false, null);
                    short[] targetPixels = ((DataBufferUShort) bufferedImage.getRaster().getDataBuffer()).getData();

                    // Copia diretta dei pixel (RGB -> RGB nativo a 16-bit)
                    System.arraycopy(rgbPixels, 0, targetPixels, 0, rgbPixels.length);

                    return bufferedImage;

                } else {
                    // --- RAMO 8-BIT (Fallback se si richiede esplicitamente outputBps = 8) ---
                    byte[] rgbPixels = pixelSegment.toArray(ValueLayout.JAVA_BYTE);

                    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] targetPixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

                    for (int i = 0; i < width * height; i++) {
                        int idx = i * 3;
                        targetPixels[idx]     = rgbPixels[idx + 2]; // Blue
                        targetPixels[idx + 1] = rgbPixels[idx + 1]; // Green
                        targetPixels[idx + 2] = rgbPixels[idx];     // Red
                    }

                    return bufferedImage;
                }

            } finally {
                rawFreeMemImageMH.invokeExact(imgProcessedPtr);
            }
        }
    }

    @Override
    public void close() {
        if (rawHandle != null && !rawHandle.equals(MemorySegment.NULL)) {
            try {
                rawCloseMH.invokeExact(rawHandle);
            } catch (Throwable t) {
                throw new RuntimeException("Errore durante la chiusura delle risorse LibRaw", t);
            }
        }
    }
}