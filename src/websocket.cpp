#include "websocket.h"

AsyncWebSocket ws("/ws");

void onWebSocketEvent(AsyncWebSocket *server, AsyncWebSocketClient *client, AwsEventType type, void *arg, uint8_t *data, size_t len) {
  switch (type) {
    case WS_EVT_CONNECT:
      Serial.printf("WebSocket client #%u connected from %s\n", client->id(), client->remoteIP().toString().c_str());
      // Send current status to newly connected client
      client->text(getCarStatusJSON());
      break;

    case WS_EVT_DISCONNECT:
      Serial.printf("WebSocket client #%u disconnected\n", client->id());
      break;

    case WS_EVT_DATA:
      handleWebSocketMessage(client, data, len);
      break;

    case WS_EVT_PONG:
    case WS_EVT_ERROR:
      break;
  }
}

void handleWebSocketMessage(AsyncWebSocketClient *client, uint8_t *data, size_t len) {
  // Parse incoming JSON command
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, data, len);

  if (error) {
    Serial.print("JSON parse error: ");
    Serial.println(error.c_str());
    return;
  }

  const char* command = doc["command"];
  const char* action = doc["action"];

  if (command == nullptr) {
    Serial.println("WebSocket: No command specified");
    return;
  }

  Serial.print("WebSocket command: ");
  Serial.print(command);
  if (action) {
    Serial.print(" action: ");
    Serial.println(action);
  } else {
    Serial.println();
  }

  // Handle commands
  if (strcmp(command, "lock") == 0) {
    vf3_car_lock = HIGH;
    vf3_car_unlock = LOW;
    digitalWrite(VF3_CAR_LOCK, HIGH);
    digitalWrite(VF3_CAR_UNLOCK, LOW);
    Serial.println("WS: Car locked");
  }
  else if (strcmp(command, "unlock") == 0) {
    vf3_car_lock = LOW;
    vf3_car_unlock = HIGH;
    digitalWrite(VF3_CAR_LOCK, LOW);
    digitalWrite(VF3_CAR_UNLOCK, HIGH);
    Serial.println("WS: Car unlocked");
  }
  else if (strcmp(command, "accessory-power") == 0 && action) {
    if (strcmp(action, "on") == 0) {
      vf3_accessory_power = HIGH;
      digitalWrite(VF3_ACCESSORY_POWER, HIGH);
      Serial.println("WS: Accessory power ON");
    } else if (strcmp(action, "off") == 0) {
      vf3_accessory_power = LOW;
      digitalWrite(VF3_ACCESSORY_POWER, LOW);
      Serial.println("WS: Accessory power OFF");
    } else if (strcmp(action, "toggle") == 0) {
      vf3_accessory_power = !vf3_accessory_power;
      digitalWrite(VF3_ACCESSORY_POWER, vf3_accessory_power);
      Serial.println("WS: Accessory power toggled");
    }
  }
  else if (strcmp(command, "windows-close") == 0) {
    window_close_timer = millis();
    digitalWrite(VF3_WINDOW_LEFT, HIGH);
    digitalWrite(VF3_WINDOW_RIGHT, HIGH);
    Serial.println("WS: Windows closing");
  }
  else if (strcmp(command, "windows-stop") == 0) {
    window_close_timer = 0;
    digitalWrite(VF3_WINDOW_LEFT, LOW);
    digitalWrite(VF3_WINDOW_RIGHT, LOW);
    Serial.println("WS: Windows stopped");
  }
  else if (strcmp(command, "buzzer") == 0 && action) {
    if (strcmp(action, "on") == 0) {
      digitalWrite(VF3_BUZZER, HIGH);
      Serial.println("WS: Buzzer ON");
    } else if (strcmp(action, "off") == 0) {
      digitalWrite(VF3_BUZZER, LOW);
      Serial.println("WS: Buzzer OFF");
    } else if (strncmp(action, "beep:", 5) == 0) {
      int duration = atoi(action + 5);
      if (duration > 0 && duration <= 5000) {
        digitalWrite(VF3_BUZZER, HIGH);
        delay(duration);
        digitalWrite(VF3_BUZZER, LOW);
        Serial.printf("WS: Buzzer beep for %dms\n", duration);
      }
    }
  }
  else if (strcmp(command, "turn-signal-left") == 0 && action) {
    if (strcmp(action, "on") == 0) {
      digitalWrite(VF3_TURN_SIGNAL_L, HIGH);
      Serial.println("WS: Left turn signal ON");
    } else if (strcmp(action, "off") == 0) {
      digitalWrite(VF3_TURN_SIGNAL_L, LOW);
      Serial.println("WS: Left turn signal OFF");
    }
  }
  else if (strcmp(command, "turn-signal-right") == 0 && action) {
    if (strcmp(action, "on") == 0) {
      digitalWrite(VF3_TURN_SIGNAL_R, HIGH);
      Serial.println("WS: Right turn signal ON");
    } else if (strcmp(action, "off") == 0) {
      digitalWrite(VF3_TURN_SIGNAL_R, LOW);
      Serial.println("WS: Right turn signal OFF");
    }
  }
  else if (strcmp(command, "turn-signal-both-off") == 0) {
    digitalWrite(VF3_TURN_SIGNAL_L, LOW);
    digitalWrite(VF3_TURN_SIGNAL_R, LOW);
    Serial.println("WS: Both turn signals OFF");
  }
  else if (strcmp(command, "status") == 0) {
    // Send status immediately to requesting client
    client->text(getCarStatusJSON());
    Serial.println("WS: Status sent");
  }

  // Broadcast updated status to all connected clients
  broadcastStatus();
}

void broadcastStatus() {
  if (ws.count() > 0) {
    ws.textAll(getCarStatusJSON());
  }
}
