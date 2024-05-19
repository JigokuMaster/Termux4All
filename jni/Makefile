

CC = arm-linux-androideabi-gcc
TARGET_LIB_armeabi = ../libs/armeabi/libtermux.so
TARGET_LIB_armv7a  = ../libs/armeabi-v7a/libtermux.so
CFLAGS = -std=c99
LDFLAGS = -shared

all: TARGET_armeabi clean_obj TARGET_armv7a

TARGET_armeabi:
	$(CC) $(CFLAGS) -c termux.c -o termux.o
	$(CC)  $(LDFLAGS) -o $@ termux.o -o $(TARGET_LIB_armeabi)


TARGET_armv7a:
	$(CC) $(CFLAGS) -march=armv7-a -c termux.c -o termux.o
	$(CC) $(LDFLAGS) termux.o -o $(TARGET_LIB_armv7a)

clean_obj:
	rm termux.o 

clean:
	clean_ob
	rm $(TARGET_LIB_armeabi) $(TARGET_LIB_armv7a)
