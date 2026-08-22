package it.aromagnoli.raw.nativeffi;

import java.nio.ByteBuffer;

public class NativeRawBridge {

    static {
        // Carica la DLL/SO nativa C++
        System.loadLibrary("raw_engine_native");
    }

    /**
     * Struttura dati per restituire metadati ed estrazione dal C++
     */
    public static class RawMetadata {
        public int width;
        public int height;
        public float rGain;
        public float gGain;
        public float bGain;
        public float[] camXyz = new float[9]; // Matrice 3x3 Camera -> XYZ
    }

    /**
     * Estrae il buffer BGR Float32 a 3 canali direttamente in un Direct ByteBuffer.
     */
    public native ByteBuffer decodeRawToDirectBuffer(String filePath, RawMetadata outMetadata);

    /**
     * Libera la memoria allocata sul lato C++
     */
    public native void freeNativeBuffer(ByteBuffer buffer);
}
