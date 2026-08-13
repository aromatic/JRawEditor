# Rilevamento Sistema Operativo
UNAME_S := $(shell uname -s)

ifeq ($(UNAME_S),Linux)
    TARGET = src/main/resources/native/libraw_wrapper.so
    FLAGS = -shared -fPIC
endif
ifeq ($(UNAME_S),Darwin)
    TARGET = src/main/resources/native/libraw_wrapper.dylib
    FLAGS = -dynamiclib
endif

CXX = g++
CXXFLAGS = -O3 -Isrc/main/cpp/include $(FLAGS)
LDFLAGS = -lraw

SRCS = src/main/cpp/libraw_wrapper.cpp

all: $(TARGET)

$(TARGET): $(SRCS)
	mkdir -p src/main/resources/native
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET) $(LDFLAGS)

clean:
	rm -f src/main/resources/native/*