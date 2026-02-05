#include "discovery.h"

// UDP Discovery Configuration
#define UDP_BROADCAST_PORT 8888
#define UDP_BROADCAST_INTERVAL 10000  // Broadcast every 10 seconds

WiFiUDP udp;
unsigned long lastBroadcast = 0;
bool broadcastConfirmed = false;

void initDiscovery() {
  // Start listening on UDP port for incoming confirmation messages
  udp.begin(UDP_BROADCAST_PORT);
  broadcastConfirmed = false;
  Serial.println("UDP Discovery initialized");
  Serial.printf("Listening for confirmations on UDP port %d\n", UDP_BROADCAST_PORT);
}

void handleDiscoveryBroadcast() {
  // Stop broadcasting if already confirmed
  if (broadcastConfirmed) {
    return;
  }

  // Only broadcast if WiFi is connected in Station mode
  if (WiFi.getMode() != WIFI_STA || WiFi.status() != WL_CONNECTED) {
    return;
  }

  // Check for incoming UDP confirmation messages
  int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    char incomingPacket[256];
    int len = udp.read(incomingPacket, sizeof(incomingPacket) - 1);
    if (len > 0) {
      incomingPacket[len] = '\0';

      // Parse incoming JSON
      JsonDocument doc;
      DeserializationError error = deserializeJson(doc, incomingPacket);

      if (!error) {
        const char* command = doc["command"];
        const char* action = doc["action"];

        // Check for confirmation message
        if ((command && strcmp(command, "confirm") == 0) ||
            (action && strcmp(action, "discovered") == 0)) {
          broadcastConfirmed = true;
          Serial.println("UDP Discovery: Received confirmation, stopping broadcast");
          Serial.printf("Confirmed by: %s\n", udp.remoteIP().toString().c_str());
        }
      }
    }
  }

  unsigned long now = millis();
  if (now - lastBroadcast < UDP_BROADCAST_INTERVAL) {
    return;
  }

  lastBroadcast = now;

  // Create JSON message with device information
  JsonDocument doc;
  doc["device"] = "VF3-Smart";
  doc["type"] = "car-control";
  doc["ip"] = WiFi.localIP().toString();
  doc["mac"] = WiFi.macAddress();
  doc["hostname"] = WiFi.getHostname();

  // Serialize JSON to string
  String message;
  serializeJson(doc, message);

  // Broadcast UDP message
  // Calculate broadcast address: (IP & subnet) | (~subnet)
  IPAddress localIP = WiFi.localIP();
  IPAddress subnet = WiFi.subnetMask();
  IPAddress broadcastIP(
    (localIP[0] & subnet[0]) | (~subnet[0] & 0xFF),
    (localIP[1] & subnet[1]) | (~subnet[1] & 0xFF),
    (localIP[2] & subnet[2]) | (~subnet[2] & 0xFF),
    (localIP[3] & subnet[3]) | (~subnet[3] & 0xFF)
  );

  udp.beginPacket(broadcastIP, UDP_BROADCAST_PORT);
  udp.print(message);
  udp.endPacket();

  Serial.print("UDP Broadcast: ");
  Serial.println(message);
  Serial.printf("Broadcasting to: %s (subnet: %s)\n",
    broadcastIP.toString().c_str(),
    subnet.toString().c_str());
}
