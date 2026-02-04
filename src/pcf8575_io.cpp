#include "pcf8575_io.h"
#include <Wire.h>

// PCF8575 instance - global for use throughout the project
PCF8575 pcf8575(PCF8575_ADDRESS, PCF8575_SDA, PCF8575_SCL);

bool initPCF8575() {
  Serial.println("Initializing PCF8575 I2C I/O Expander...");

  // Initialize I2C
  Wire.begin(PCF8575_SDA, PCF8575_SCL);

  // Initialize PCF8575
  if (!pcf8575.begin()) {
    Serial.println("ERROR: PCF8575 begin() failed!");
    return false;
  }

  // Test communication by doing a read
  pcf8575.digitalRead(0);

  if (!pcf8575.isLastTransmissionSuccess()) {
    Serial.println("ERROR: PCF8575 not found at address 0x" + String(PCF8575_ADDRESS, HEX));
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

  return true;
}
