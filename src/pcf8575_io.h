#ifndef PCF8575_IO_H
#define PCF8575_IO_H

#include <Arduino.h>
#include <PCF8575.h>

// PCF8575 I2C address (default is 0x20, adjust if different)
#define PCF8575_ADDRESS 0x20

// PCF8575 I2C pins
#define PCF8575_SDA 21  // GPIO 21
#define PCF8575_SCL 22  // GPIO 22

// PCF8575 pin definitions (P0-P15)
// These match the physical pins on the PCF8575
#define PCF_P0  0
#define PCF_P1  1
#define PCF_P2  2
#define PCF_P3  3
#define PCF_P4  4
#define PCF_P5  5
#define PCF_P6  6
#define PCF_P7  7
#define PCF_P8  8
#define PCF_P9  9
#define PCF_P10 10
#define PCF_P11 11
#define PCF_P12 12
#define PCF_P13 13
#define PCF_P14 14
#define PCF_P15 15

// Initialize PCF8575
bool initPCF8575();

// Digital I/O functions
void pcfDigitalWrite(uint8_t pin, uint8_t value);
int pcfDigitalRead(uint8_t pin);

// Batch operations for efficiency
void pcfWriteAll(uint16_t value);
uint16_t pcfReadAll();

// Get PCF8575 instance (for advanced usage)
extern PCF8575 pcf8575;

#endif // PCF8575_IO_H
