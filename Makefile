# Rilevamento Sistema Operativo
ifeq ($(OS),Windows_NT)
    UNAME_S := Windows
    # Su Windows usa 'mkdir' senza '-p' e converti i slash se necessario
    MKDIR = if not exist "src\main\resources\native" mkdir "src\main\resources\native"
    RM = del /Q /F
else
    UNAME_S := $(shell uname -s)
    MKDIR = mkdir -p src/main/resources/native
    RM = rm -f
endif

# Variabile JAVA_HOME
JAVA_HOME ?= $(shell echo $$JAVA_HOME)

# Impostazioni di default
CXX = g++
INCLUDES = -Isrc/main/cpp/include

ifneq ($(JAVA_HOME),)
    INCLUDES += -I"$(JAVA_HOME)/include"
endif

# OS Specifics
ifeq ($(UNAME_S),Linux)
    TARGET = src/main/resources/native/libraw_wrapper.so
    FLAGS = -shared -fPIC
    ifneq ($(JAVA_HOME),)
        INCLUDES += -I"$(JAVA_HOME)/include/linux"
    endif
endif

ifeq ($(UNAME_S),Darwin)
    TARGET = src/main/resources/native/libraw_wrapper.dylib
    FLAGS = -dynamiclib
    ifneq ($(JAVA_HOME),)
        INCLUDES += -I"$(JAVA_HOME)/include/darwin"
    endif
endif

ifeq ($(UNAME_S),Windows)
    TARGET = src/main/resources/native/raw_wrapper.dll
    FLAGS = -shared
    ifneq ($(JAVA_HOME),)
        INCLUDES += -I"$(JAVA_HOME)/include/win32"
    endif
endif

CXXFLAGS = -O3 $(INCLUDES) $(FLAGS)
LDFLAGS = -lraw

SRCS = src/main/cpp/libraw_wrapper.cpp

all: $(TARGET)

$(TARGET): $(SRCS)
	$(MKDIR)
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET) $(LDFLAGS)

win64:
	$(MKDIR)
	x86_64-w64-mingw32-g++ -O3 $(INCLUDES) -I"$(JAVA_HOME)/include/win32" -shared $(SRCS) -o src/main/resources/native/raw_wrapper.dll $(LDFLAGS)

clean:
	$(RM) src/main/resources/native/*