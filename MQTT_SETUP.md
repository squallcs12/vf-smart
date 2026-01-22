# MQTT Configuration Setup

## Overview

MQTT credentials are stored in a local configuration file that is **not tracked by git** for security purposes.

## Setup Instructions

1. **Copy the template file:**
   ```bash
   cp include/mqtt_config.h.example include/mqtt_config.h
   ```

2. **Edit `include/mqtt_config.h`** with your MQTT broker credentials:
   ```cpp
   #define MQTT_BROKER "your-mqtt-broker.com"
   #define MQTT_PORT 1883
   #define MQTT_USERNAME "your-username"
   #define MQTT_PASSWORD "your-password"
   ```

3. **Build the project:**
   ```bash
   pio run
   ```

## Security Note

- `include/mqtt_config.h` is listed in `.gitignore` and will **never be committed** to git
- `include/mqtt_config.h.example` is tracked in git as a template
- Each developer/deployment must create their own `mqtt_config.h` file

## Current Configuration

The project is currently configured to use CloudAMQP broker:
- Broker: `fuji.lmq.cloudamqp.com`
- Port: `1883`
- Username and password are in your local `mqtt_config.h` file

## MQTT Topics

The device uses the API key as the MQTT topic prefix and implements a request/response pattern:

### Command Topics (Device Subscribes)
- `[api_key]/command/lock` - Lock the car
- `[api_key]/command/unlock` - Unlock the car
- `[api_key]/command/accessory-power` - Control accessory power (on/off/toggle)
- `[api_key]/command/windows/close` - Close windows
- `[api_key]/command/windows/stop` - Stop window operation
- `[api_key]/command/buzzer` - Control buzzer (on/off/beep:duration)
- `[api_key]/command/turn-signal/left` - Left turn signal (on/off)
- `[api_key]/command/turn-signal/right` - Right turn signal (on/off)
- `[api_key]/command/turn-signal/both-off` - Turn off both turn signals

### Request Topics (Device Subscribes)
- `[api_key]/request/status` - Request car status (device responds by publishing to status topic)

### Response Topics (Device Publishes)
- `[api_key]/status` - Car status (published only when requested via request/status topic)

## Usage Example

To get the current car status:
1. Publish an empty message to `[api_key]/request/status`
2. Subscribe to `[api_key]/status` to receive the response
