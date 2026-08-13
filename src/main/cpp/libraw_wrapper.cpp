#include "include/libraw_wrapper.h"
#include <libraw/libraw.h>

extern "C" {

libraw_handle_t raw_init() {
    return (libraw_handle_t) libraw_init(0);
}

int raw_open_and_unpack(libraw_handle_t handle, const char* filepath) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    int ret = libraw_open_file(lr, filepath);
    if (ret != LIBRAW_SUCCESS) return ret;
    return libraw_unpack(lr);
}

int raw_process_image(libraw_handle_t handle, int half_size, int use_camera_wb, int use_auto_wb) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    lr->params.half_size = half_size;
    lr->params.use_camera_wb = use_camera_wb;
    lr->params.use_auto_wb = use_auto_wb;
    lr->params.output_color = 1; // sRGB
    lr->params.output_bps = 8;

    return libraw_dcraw_process(lr);
}

void* raw_get_processed_image(libraw_handle_t handle) {
    libraw_data_t* lr = (libraw_data_t*) handle;
    int err = 0;
    return (void*) libraw_dcraw_make_mem_image(lr, &err);
}

// SOSTITUITO ushort CON unsigned short
unsigned short raw_get_width(void* processed_img) {
    if (!processed_img) return 0;
    return ((libraw_processed_image_t*) processed_img)->width;
}

unsigned short raw_get_height(void* processed_img) {
    if (!processed_img) return 0;
    return ((libraw_processed_image_t*) processed_img)->height;
}

unsigned int raw_get_data_size(void* processed_img) {
    if (!processed_img) return 0;
    return ((libraw_processed_image_t*) processed_img)->data_size;
}

unsigned char* raw_get_data_ptr(void* processed_img) {
    if (!processed_img) return nullptr;
    return ((libraw_processed_image_t*) processed_img)->data;
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

}