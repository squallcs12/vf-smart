#ifndef DISCOVERY_H
#define DISCOVERY_H

#include <WiFi.h>
#include <WiFiUdp.h>
#include <ArduinoJson.h>

void initDiscovery();
void handleDiscoveryBroadcast();

#endif // DISCOVERY_H
