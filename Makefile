# Rilevamento Sistema Operativo
ifeq ($(OS),Windows_NT)
    DETECTED_OS := Windows
else
    DETECTED_OS := $(shell uname -s)
endif

# Configurazione specifica per SO
ifeq ($(DETECTED_OS),Windows)
    TARGET = src/main/resources/native/libraw_wrapper.dll
    FLAGS = -shared
    # Comando cross-platform per creare cartelle su Windows
    MKDIR_P = if not exist "src\main\resources\native" mkdir "src\main\resources\native"
    # Comando cross-platform per rimuovere i file
    RM = del /Q /F
    TARGET_CLEAN = src\main\resources\native\*
else ifeq ($(DETECTED_OS),Linux)
    TARGET = src/main/resources/native/libraw_wrapper.so
    FLAGS = -shared -fPIC
    MKDIR_P = mkdir -p src/main/resources/native
    RM = rm -f
    TARGET_CLEAN = src/main/resources/native/*
else ifeq ($(DETECTED_OS),Darwin)
    TARGET = src/main/resources/native/libraw_wrapper.dylib
    FLAGS = -dynamiclib
    MKDIR_P = mkdir -p src/main/resources/native
    RM = rm -f
    TARGET_CLEAN = src/main/resources/native/*
endif

CXX = g++
CXXFLAGS = -O3 -Isrc/main/cpp/include $(FLAGS)
LDFLAGS = -lraw

SRCS = src/main/cpp/libraw_wrapper.cpp

all: $(TARGET)

$(TARGET): $(SRCS)
	$(MKDIR_P)
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET) $(LDFLAGS)

clean:
	$(RM) $(TARGET_CLEAN)