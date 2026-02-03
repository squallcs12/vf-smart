#include "pcf8575_io.h"
#include <Wire.h>

// PCF8575 instance
PCF8575 pcf8575(PCF8575_ADDRESS, PCF8575_SDA, PCF8575_SCL);

// Internal state tracking to minimize I2C transactions
static uint16_t output_state = 0x0000;  // Track output pin states
static bool pcf_initialized = false;

bool initPCF8575() {
  if (pcf_initialized) {
    return true;
  }

  Serial.println("Initializing PCF8575 I2C I/O Expander...");

  // Initialize I2C
  Wire.begin(PCF8575_SDA, PCF8575_SCL);

  // Initialize PCF8575
  // Set all pins as inputs initially (HIGH = input mode for PCF8575)
  pcf8575.begin();

  // Test communication
  if (!pcf8575.isConnected()) {
    Serial.println("ERROR: PCF8575 not found at address 0x" + String(PCF8575_ADDRESS, HEX));
    return false;
  }

  Serial.print("PCF8575 connected at address: 0x");
  Serial.println(PCF8575_ADDRESS, HEX);

  // Initialize all pins to LOW (output mode)
  output_state = 0x0000;
  pcf8575.write16(output_state);

  pcf_initialized = true;
  Serial.println("PCF8575 initialized successfully");

  return true;
}

void pcfDigitalWrite(uint8_t pin, uint8_t value) {
  if (!pcf_initialized) {
    Serial.println("ERROR: PCF8575 not initialized!");
    return;
  }

  if (pin > 15) {
    Serial.println("ERROR: Invalid PCF8575 pin: " + String(pin));
    return;
  }

  // Update the output state
  if (value == HIGH) {
    output_state |= (1 << pin);  // Set bit
  } else {
    output_state &= ~(1 << pin); // Clear bit
  }

  // Write to PCF8575
  pcf8575.write16(output_state);
}

int pcfDigitalRead(uint8_t pin) {
  if (!pcf_initialized) {
    Serial.println("ERROR: PCF8575 not initialized!");
    return LOW;
  }

  if (pin > 15) {
    Serial.println("ERROR: Invalid PCF8575 pin: " + String(pin));
    return LOW;
  }

  // Read all pins from PCF8575
  uint16_t input_state = pcf8575.read16();

  // Extract the specific pin value
  return (input_state & (1 << pin)) ? HIGH : LOW;
}

void pcfWriteAll(uint16_t value) {
  if (!pcf_initialized) {
    Serial.println("ERROR: PCF8575 not initialized!");
    return;
  }

  output_state = value;
  pcf8575.write16(output_state);
}

uint16_t pcfReadAll() {
  if (!pcf_initialized) {
    Serial.println("ERROR: PCF8575 not initialized!");
    return 0;
  }

  return pcf8575.read16();
}
