#include "include/libraw_wrapper.h"
#include <libraw/libraw.h>

extern "C" {

libraw_handle_t raw_init() {
    return (libraw_handle_t) libraw_init(0);
}

int raw_open_and_unpack(libraw_handle_t handle, const char* filepath) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    if (!lr) return LIBRAW_OUT_OF_ORDER_CALL;

    int ret = libraw_open_file(lr, filepath);
    if (ret != LIBRAW_SUCCESS) return ret;
    
    return libraw_unpack(lr);
}

int raw_process_image(libraw_handle_t handle, int half_size, int use_camera_wb, int use_auto_wb) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    if (!lr) return LIBRAW_OUT_OF_ORDER_CALL;

    lr->params.half_size = half_size;
    lr->params.use_camera_wb = use_camera_wb;
    lr->params.use_auto_wb = use_auto_wb;
    lr->params.output_color = 1; // sRGB
    lr->params.output_bps = 8;

    return libraw_dcraw_process(lr);
}

/**
 * Elabora l'immagine garantendo un output strettamente lineare.
 */
int raw_process_linear(libraw_handle_t handle, 
                       int use_camera_wb, 
                       int use_auto_wb, 
                       const float user_mul[4], 
                       int demosaic_algo, 
                       int output_color, 
                       int output_bps, 
                       int half_size) 
{
    libraw_data_t* lr = (libraw_data_t*) handle;
    
    // ✅ CORRETTO: Sostituito LIBRAW_BAD_ORDER con LIBRAW_OUT_OF_ORDER_CALL
    if (!lr) return LIBRAW_OUT_OF_ORDER_CALL; 

    // --- FORZATURA CURVA GAMMA LINEARE ---
    // gamm[0] = 1.0/power (1.0 = lineare)
    // gamm[1] = slope (1.0 = nessuna curva/linear)
    lr->params.gamm[0] = 1.0;
    lr->params.gamm[1] = 1.0;

    // --- AZZERAMENTO ALTERAZIONI DINAMICHE ---
    lr->params.no_auto_bright = 1; // Blocca la scalatura automatica dell'istogramma
    lr->params.bright = 1.0f;       // Guadagno unitario

    // --- GESTIONE BILANCIAMENTO DEL BIANCO ---
    lr->params.use_camera_wb = use_camera_wb;
    lr->params.use_auto_wb = use_auto_wb;
    
    if (user_mul != nullptr) {
        lr->params.user_mul[0] = user_mul[0]; // R
        lr->params.user_mul[1] = user_mul[1]; // G1
        lr->params.user_mul[2] = user_mul[2]; // B
        lr->params.user_mul[3] = user_mul[3]; // G2
        
        lr->params.use_camera_wb = 0;
        lr->params.use_auto_wb = 0;
    }

    // --- CONFIGURAZIONE ELABORAZIONE ---
    lr->params.user_qual = demosaic_algo;
    lr->params.output_color = output_color;
    lr->params.output_bps = output_bps;
    lr->params.half_size = half_size;

    return libraw_dcraw_process(lr);
}

void* raw_get_processed_image(libraw_handle_t handle) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    if (!lr) return nullptr;

    int err = 0;
    return (void*) libraw_dcraw_make_mem_image(lr, &err);
}

void* raw_get_data_ptr(void* processed_img) {
    if (!processed_img) return nullptr;
    libraw_processed_image_t* img = (libraw_processed_image_t*) processed_img;
    return (void*) img->data;
}

unsigned short raw_get_width(void* processed_img) {
    if (!processed_img) return 0;
    libraw_processed_image_t* img = (libraw_processed_image_t*) processed_img;
    return img->width;
}

unsigned short raw_get_height(void* processed_img) {
    if (!processed_img) return 0;
    libraw_processed_image_t* img = (libraw_processed_image_t*) processed_img;
    return img->height;
}

unsigned int raw_get_data_size(void* processed_img) {
    if (!processed_img) return 0;
    libraw_processed_image_t* img = (libraw_processed_image_t*) processed_img;
    return img->data_size;
}

void raw_free_mem_image(void* img) {
    if (img) {
        libraw_dcraw_clear_mem((libraw_processed_image_t*) img);
    }
}

void raw_close(libraw_handle_t handle) {
    if (handle) {
        libraw_close((libraw_data_t*) handle);
    }
}


/**
 * Converte un file RAW in TIFF a 16-bit con opzioni avanzate.
 * 
 * @param inputPath     Percorso del file RAW sorgente
 * @param outputPath    Percorso di destinazione (.tif)
 * @param colorSpace    2 = AdobeRGB, 4 = ProPhotoRGB, 1 = sRGB
 * @param demosaicAlgo  12 = RCD (Consigliato), 3 = AHD, 1 = VNG
 * @param useCameraWB   true = Usa il WB della fotocamera, false = WB Automatico
 * @param noiseThresh   Soglia riduzione rumore Wavelet (es. 0.0 per disabilitare, 250.0 per ISO alti)
 * @param noiseIteration Numero di iterazioni del filtro mediano (es. 0 per disabilitare, 1-3 per ISO alti)
 */
bool convertRawToTiffAdvanced(const char* inputPath, 
                             const char* outputPath, 
                             int colorSpace, 
                             int demosaicAlgo, 
                             bool useCameraWB, 
                             float noiseThresh,
                             int noiseIteration) 
{
    LibRaw processor;

    // 1. Apertura e estrazione
    if (processor.open_file(inputPath) != LIBRAW_SUCCESS) return false;
    if (processor.unpack() != LIBRAW_SUCCESS) return false;

    // 2. Output 16-bit e Spazio Colore
    processor.imgdata.params.output_bps = 16;
    processor.imgdata.params.output_color = colorSpace;

    // 3. Algoritmo di Demosaicing
    processor.imgdata.params.user_qual = demosaicAlgo;

    // 4. Bilanciamento del Bianco
    if (useCameraWB) {
        processor.imgdata.params.use_camera_wb = 1; // WB Fotocamera
    } else {
        processor.imgdata.params.use_auto_wb = 1;   // WB Automatico
    }

    // 5. Riduzione del Rumore
    if (noiseThresh > 0.0f) {
        processor.imgdata.params.threshold = noiseThresh; // Wavelet Denoise
        processor.imgdata.params.med_passes = noiseIteration; // Filtro mediano a N passaggi
    }

    // 6. Gamma e Luminosità (Linearità per 16-bit)
    processor.imgdata.params.gamm[0] = 1.0;
    processor.imgdata.params.gamm[1] = 1.0;
    processor.imgdata.params.no_auto_bright = 1;

    // 7. Elaborazione e Salvataggio
    if (processor.dcraw_process() != LIBRAW_SUCCESS) return false;
    if (processor.dcraw_ppm_tiff_writer(outputPath) != LIBRAW_SUCCESS) return false;

    processor.recycle();
    return true;
}

}