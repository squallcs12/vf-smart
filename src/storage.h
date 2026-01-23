#ifndef STORAGE_H
#define STORAGE_H

#include <Arduino.h>
#include "config.h"

void loadConfiguration();
void saveConfiguration(String ssid, String password, String api_key);

#endif // STORAGE_H
