
#include "globals.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ESPmDNS.h>
#include <ArduinoOTA.h>
#include <time.h>
#include <BLEDevice.h> // <--- Added this include

// Pre-declare functions from other modules that are used here
void drawOtaScreen(String text, int progress = -1);
void updateScreens();

void startWiFiConnection() {
    Serial.println("DEBUG: startWiFiConnection");
    if (wifiState == WIFI_CONNECTING) return;
    wifiState = WIFI_CONNECTING;
    wifiConnectionStartTime = millis();
    preferences.begin("wifi", true);
    String savedSSID = preferences.getString("ssid", default_ssid);
    String savedPASS = preferences.getString("pass", default_password);
    preferences.end();
    Serial.printf("DEBUG: Saved SSID from preferences: %s\n", savedSSID.c_str());
    WiFi.disconnect(true);
    delay(100);
    Serial.println("DEBUG: Attempting to connect to WiFi...");
    WiFi.begin(savedSSID.c_str(), savedPASS.c_str());
    Serial.println("Starting WiFi connection...");
}

void handleWiFiConnection() {
    Serial.println("DEBUG: handleWiFiConnection");
    if (wifiState != WIFI_CONNECTING) return;
    if (WiFi.status() == WL_CONNECTED) {
        wifiState = WIFI_CONNECTED;
        Serial.printf("DEBUG: WiFi connected, IP: %s\n", WiFi.localIP().toString().c_str());
        Serial.print("WiFi Connected! IP: ");
        Serial.println(WiFi.localIP());
        syncTimeNTPForce();
        fetchWeatherData();
        if (MDNS.begin("smartmedbox")) {
            Serial.println("mDNS responder started");
        }
        setupOTA();
    } else if (millis() - wifiConnectionStartTime > 15000) {
        wifiState = WIFI_FAILED;
        WiFi.disconnect(true);
        Serial.println("DEBUG: WiFi connection failed, timeout.");
        Serial.println("WiFi Connection Failed (Timeout).");
    }
}

void syncTimeNTPForce() {
    Serial.println("DEBUG: syncTimeNTPForce");
    if (wifiState != WIFI_CONNECTED) return;
    configTime(GMT_OFFSET, DAYLIGHT_OFFSET, NTP_SERVER);
    struct tm timeinfo;
    if (getLocalTime(&timeinfo, 5000)) {
        syncIconStartTime = millis();
        lastNTPResync = millis();
        Serial.println("DEBUG: NTP Time synced successfully.");
        Serial.println("NTP Time synced.");
    } else {
        Serial.println("DEBUG: NTP Time sync failed.");
        Serial.println("NTP Time sync failed.");
    }
}

void fetchWeatherData() {
    Serial.println("DEBUG: fetchWeatherData");
    if (wifiState != WIFI_CONNECTED) { weatherData.valid = false; return; }
    HTTPClient http;
    String url = "http://api.openweathermap.org/data/2.5/weather?q=" + city + "," + countryCode + "&units=metric&lang=zh_tw&APPID=" + openWeatherMapApiKey;
    Serial.printf("DEBUG: Fetching weather data from URL: %s\n", url.c_str());
    http.begin(url);
    int httpCode = http.GET();
    if (httpCode > 0) {
        Serial.println("DEBUG: HTTP GET successful, parsing payload.");
        String payload = http.getString();
        int d1 = payload.indexOf("\"description\":\"") + 15;
        int d2 = payload.indexOf("\"", d1);
        if (d1 > 14 && d2 > d1) weatherData.description = payload.substring(d1, d2);
        int t1 = payload.indexOf("\"temp\":") + 7;
        int t2 = payload.indexOf(",", t1);
        if (t1 > 6 && t2 > t1) weatherData.temp = payload.substring(t1, t2).toFloat();
        int h1 = payload.indexOf("\"humidity\":") + 11;
        int h2 = payload.indexOf("}", h1);
        if (h1 > 10 && h2 > h1) weatherData.humidity = payload.substring(h1, h2).toInt();
        weatherData.valid = true;
        Serial.printf("DEBUG: Weather data parsed: %s, %.1fC, %d%%\n", weatherData.description.c_str(), weatherData.temp, weatherData.humidity);
    } else {
        weatherData.valid = false;
        Serial.printf("DEBUG: Weather fetch failed with error: %s\n", http.errorToString(httpCode).c_str());
        Serial.printf("Weather fetch failed, error: %s\n", http.errorToString(httpCode).c_str());
    }
    http.end();
}

void setupOTA() {
    Serial.println("DEBUG: setupOTA");
    ArduinoOTA.setHostname("smartmedbox");
    ArduinoOTA.setPassword("medbox123");
    ArduinoOTA
        .onStart( [] { SPIFFS.end(); String type = (ArduinoOTA.getCommand() == U_FLASH) ? "sketch" : "filesystem"; drawOtaScreen("Updating " + type, 0); })
        .onProgress([](unsigned int progress, unsigned int total) { drawOtaScreen("Updating...", (progress / (total / 100))); })
        .onEnd( [] { drawOtaScreen("Complete!", 100); delay(1000); ESP.restart(); })
        .onError([](ota_error_t error) {
            String msg;
            if (error == OTA_AUTH_ERROR) msg = "Auth Failed";
            else if (error == OTA_BEGIN_ERROR) msg = "Begin Failed";
            else if (error == OTA_CONNECT_ERROR) msg = "Connect Failed";
            else if (error == OTA_RECEIVE_ERROR) msg = "Receive Failed";
            else if (error == OTA_END_ERROR) msg = "End Failed";
            drawOtaScreen("Error: " + msg, -1);
            delay(3000);
            ESP.restart();
        });
    ArduinoOTA.begin();
    Serial.println("DEBUG: OTA service is ready.");
    Serial.println("OTA service ready.");
}

void enterOtaMode() {
    Serial.println("DEBUG: enterOtaMode");
    if (isOtaMode || wifiState != WIFI_CONNECTED) {
        Serial.println("DEBUG: WiFi not connected, cannot enter OTA mode.");
        u8g2.clearBuffer();
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr((128 - u8g2.getStrWidth("Need WiFi for OTA")) / 2, 38, "Need WiFi for OTA");
        u8g2.sendBuffer();
        delay(2000);
        currentUIMode = UI_MODE_MAIN_SCREENS;
        updateScreens();
        return;
    };
    isOtaMode = true;
    Serial.println("DEBUG: Entering OTA mode...");
    Serial.println("Entering OTA mode...");
    lastBackPressTime = millis();
    Serial.println("DEBUG: BLE deinitialized for OTA.");
    BLEDevice::deinit(true);
    String ip = WiFi.localIP().toString();
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 12, "OTA Update Mode");
    u8g2.setFont(u8g2_font_profont11_tf);
    u8g2.drawStr(0, 28, "smartmedbox.local");
    u8g2.drawStr(0, 42, ("IP: " + ip).c_str());
    u8g2.drawStr(0, 56, "Press BACK to exit");
    u8g2.sendBuffer();
    Serial.println("DEBUG: Displaying OTA information screen.");
}
