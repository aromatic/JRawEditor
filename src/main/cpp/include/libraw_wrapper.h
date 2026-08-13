#ifndef LIBRAW_WRAPPER_H
#define LIBRAW_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void* libraw_handle_t;

libraw_handle_t raw_init();
int raw_open_and_unpack(libraw_handle_t handle, const char* filepath);
int raw_process_image(libraw_handle_t handle, int half_size, int use_camera_wb, int use_auto_wb);
void* raw_get_processed_image(libraw_handle_t handle);

// SOSTITUITO ushort CON unsigned short
unsigned short raw_get_width(void* processed_img);
unsigned short raw_get_height(void* processed_img);
unsigned int raw_get_data_size(void* processed_img);
unsigned char* raw_get_data_ptr(void* processed_img);

void raw_free_mem_image(void* img);
void raw_close(libraw_handle_t handle);

#ifdef __cplusplus
}
#endif

#endif