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

// Check if PCF8575 is initialized and ready
bool isPCF8575Ready();

// Safe PCF8575 operations (check initialization before accessing)
void safeDigitalWrite(uint8_t pin, uint8_t value);
uint8_t safeDigitalRead(uint8_t pin);

// Global PCF8575 instance - use pcf8575.digitalWrite(), pcf8575.digitalRead(), etc.
extern PCF8575 pcf8575;

#endif // PCF8575_IO_H
