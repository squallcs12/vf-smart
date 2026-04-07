#include "tpms_endpoint.h"
#include "auth.h"
#include "../tpms.h"
#include <ArduinoJson.h>

// Position name → index helper
static int posIndex(const String& name) {
    if (name == "fl") return 0;
    if (name == "fr") return 1;
    if (name == "rl") return 2;
    if (name == "rr") return 3;
    return -1;
}

void registerTpmsEndpoint(AsyncWebServer& server) {

    // GET /tpms/calibrate — current sensor ID assignments (no auth)
    server.on("/tpms/calibrate", HTTP_GET, [](AsyncWebServerRequest *request) {
        request->send(200, "application/json", tpmsCalibrationJSON());
    });

    // POST /tpms/calibrate — modify assignments (auth required)
    // Params:
    //   action=reset            — clear all learned IDs
    //   action=swap&a=fl&b=fr   — swap two positions
    server.on("/tpms/calibrate", HTTP_POST, [](AsyncWebServerRequest *request) {
        if (!authenticateRequest(request)) {
            sendUnauthorized(request);
            return;
        }

        String action = request->hasParam("action", true)
            ? request->getParam("action", true)->value() : "";

        JsonDocument doc;

        if (action == "reset") {
            tpmsResetAll();
            doc["success"] = true;
            doc["message"] = "All TPMS sensor assignments cleared";
        } else if (action == "swap") {
            String a = request->hasParam("a", true)
                ? request->getParam("a", true)->value() : "";
            String b = request->hasParam("b", true)
                ? request->getParam("b", true)->value() : "";

            int idxA = posIndex(a);
            int idxB = posIndex(b);

            if (idxA < 0 || idxB < 0) {
                doc["success"] = false;
                doc["message"] = "Invalid position. Use fl/fr/rl/rr";
            } else {
                tpmsSwap(idxA, idxB);
                doc["success"] = true;
                doc["message"] = "Swapped " + a + " and " + b;
            }
        } else {
            doc["success"] = false;
            doc["message"] = "Unknown action. Use reset or swap";
        }

        // Always return updated assignments
        JsonDocument calDoc;
        deserializeJson(calDoc, tpmsCalibrationJSON());
        doc["sensors"] = calDoc["sensors"];

        String output;
        serializeJson(doc, output);
        request->send(200, "application/json", output);
    });
}
