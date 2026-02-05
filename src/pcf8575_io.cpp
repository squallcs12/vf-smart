#include "pcf8575_io.h"
#include <Wire.h>

// PCF8575 instance - global for use throughout the project
PCF8575 pcf8575(PCF8575_ADDRESS, PCF8575_SDA, PCF8575_SCL);

// Global flag to track PCF8575 initialization status
static bool pcf8575_initialized = false;

bool initPCF8575() {
  Serial.println("Initializing PCF8575 I2C I/O Expander...");

  // Initialize I2C
  Wire.begin(PCF8575_SDA, PCF8575_SCL);

  // Initialize PCF8575
  if (!pcf8575.begin()) {
    Serial.println("ERROR: PCF8575 begin() failed!");
    pcf8575_initialized = false;
    return false;
  }

  // Test communication by doing a read
  pcf8575.digitalRead(0);

  if (!pcf8575.isLastTransmissionSuccess()) {
    Serial.println("ERROR: PCF8575 not found at address 0x" + String(PCF8575_ADDRESS, HEX));
    pcf8575_initialized = false;
    return false;
  }

  Serial.print("PCF8575 connected at address: 0x");
  Serial.println(PCF8575_ADDRESS, HEX);

  // Set all pins as outputs and initialize to LOW
  for (uint8_t pin = 0; pin < 16; pin++) {
    pcf8575.pinMode(pin, OUTPUT);
    pcf8575.digitalWrite(pin, LOW);
  }

  Serial.println("PCF8575 initialized successfully");

  pcf8575_initialized = true;
  return true;
}

bool isPCF8575Ready() {
  return pcf8575_initialized;
}

void safeDigitalWrite(uint8_t pin, uint8_t value) {
  if (!pcf8575_initialized) {
    Serial.println("WARNING: PCF8575 not initialized, skipping digitalWrite");
    return;
  }
  pcf8575.digitalWrite(pin, value);
}

uint8_t safeDigitalRead(uint8_t pin) {
  if (!pcf8575_initialized) {
    Serial.println("WARNING: PCF8575 not initialized, skipping digitalRead");
    return LOW;  // Return LOW as default safe value
  }
  return pcf8575.digitalRead(pin);
}
