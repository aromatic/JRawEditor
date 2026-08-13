#ifndef LIBRAW_WRAPPER_H
#define LIBRAW_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void* libraw_handle_t;

libraw_handle_t raw_init();
int raw_open_and_unpack(libraw_handle_t handle, const char* filepath);
int raw_process_image(libraw_handle_t handle, int half_size, int use_camera_wb, int use_auto_wb);
int raw_process_linear(libraw_handle_t handle, 
                       int use_camera_wb, 
                       int use_auto_wb, 
                       const float user_mul[4], 
                       int demosaic_algo, 
                       int output_color, 
                       int output_bps, 
                       int half_size);

void* raw_get_processed_image(libraw_handle_t handle);

// Usiamo tipi C standard portabili (unsigned short -> 16 bit, unsigned int -> 32 bit)
unsigned short raw_get_width(void* processed_img);
unsigned short raw_get_height(void* processed_img);
unsigned int raw_get_data_size(void* processed_img);

// IMPORTANTE: Usa void* per il buffer di dati, così è opaco rispetto a 8-bit (uint8_t) e 16-bit (uint16_t)
void* raw_get_data_ptr(void* processed_img);

void raw_free_mem_image(void* img);
void raw_close(libraw_handle_t handle);

#ifdef __cplusplus
}
#endif

#endif // LIBRAW_WRAPPER_H