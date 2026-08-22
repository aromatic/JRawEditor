package it.aromagnoli.raw.nativeffi;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class LibRawFFM implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle rawInitMH;
    private static final MethodHandle rawOpenAndUnpackMH;
    private static final MethodHandle rawProcessImageMH;
    private static final MethodHandle rawGetProcessedImageMH;
    private static final MethodHandle rawGetWidthMH;
    private static final MethodHandle rawGetHeightMH;
    private static final MethodHandle rawGetDataSizeMH;
    private static final MethodHandle rawGetDataPtrMH;
    private static final MethodHandle rawFreeMemImageMH;
    private static final MethodHandle rawCloseMH;

    static {
		String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            System.out.println("Sei su Windows");
			System.loadLibrary("libraw_wrapper.dll");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            System.out.println("Sei su Linux");
			System.loadLibrary("libraw_wrapper.so");
        } else if (os.contains("mac")) {
            System.out.println("Sei su macOS");
			System.loadLibrary("libraw_wrapper.dylib");
        } else {
            System.out.println("Sistema operativo non riconosciuto: " + os);
        }
		
        
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