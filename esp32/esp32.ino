
#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <U8g2lib.h>
#include <AiEsp32RotaryEncoder.h>
#include <DHT.h>
#include <time.h>
#include <sys/time.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Preferences.h>
#include "SPIFFS.h"
#include <ESPmDNS.h>
#include <ArduinoOTA.h>
// #include <Servo.h> // No longer needed, using native LEDC for motor control
#include <Adafruit_NeoPixel.h>

#include "src/config.h"
#include "src/globals.h"
#include "src/ble_handler.h"
#include "src/display.h"
#include "src/hardware.h"
#include "src/input.h"
#include "src/storage.h"
#include "src/wifi_ota.h"

// ==================== 全域物件與變數實例化 ====================

// ---- 硬體物件 ----
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
// Servo sg90; // No longer needed
Adafruit_NeoPixel pixels(NUM_LEDS, WS2812_PIN, NEO_GRB + NEO_KHZ800);

// ---- Wi-Fi & NTP & OTA 設定 ----
const char* default_ssid = "charlie phone";
const char* default_password = "12345678";
String openWeatherMapApiKey = "ac1003d80943887d3d29d609afea98db";
String city = "Taipei";
String countryCode = "TW";
const float TEMP_CALIBRATION_OFFSET = 2.4;
const char* NTP_SERVER = "time.google.com";
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;

// ---- BLE ----
BLECharacteristic *pDataEventCharacteristic = NULL;

// ---- 儲存 ----
Preferences preferences;
File historyFile;

// ---- 狀態與數據 ----
WiFiState wifiState = WIFI_IDLE;
unsigned long wifiConnectionStartTime = 0;
UIMode currentUIMode = UI_MODE_MAIN_SCREENS;
SystemMenuItem selectedMenuItem = MENU_ITEM_WIFI;
int menuViewOffset = 0;
const int MAX_MENU_ITEMS_ON_SCREEN = 4;
EncoderMode currentEncoderMode = MODE_NAVIGATION;
int NUM_SCREENS = 4;
ScreenState currentPageIndex = SCREEN_TIME;
WeatherData weatherData;
DataPoint historyWindowBuffer[HISTORY_WINDOW_SIZE];
int historyIndex = 0;
int historyCount = 0;
int historyViewOffset = 0;
bool bleDeviceConnected = false;
bool isEngineeringMode = false;
bool isOtaMode = false;
bool isSendingHistoricData = false;
int historicDataIndexToSend = 0;
unsigned long historicDataStartTime = 0;
unsigned long lastDisplayUpdate = 0;
const unsigned long displayInterval = 100;
unsigned long lastHistoryRecord = 0;
const unsigned long historyRecordInterval = 30000;
unsigned long lastEncoderPushTime = 0;
unsigned long lastConfirmPressTime = 0;
unsigned long confirmPressStartTime = 0;
bool confirmButtonPressed = false;
unsigned long syncIconStartTime = 0;
const unsigned long SYNC_ICON_DURATION = 3000;
unsigned long lastNTPResync = 0;
const unsigned long NTP_RESYNC_INTERVAL = 12 * 3600000;
unsigned long lastWeatherUpdate = 0;
const unsigned long WEATHER_INTERVAL = 600000;
unsigned long lastBackPressTime = 0;
bool isRealtimeEnabled = false;
unsigned long lastRealtimeSend = 0;
const unsigned long REALTIME_INTERVAL = 2000;
float cachedTemp = 0.0;
float cachedHum = 0.0;
bool sensorDataValid = false;
unsigned long lastSensorReadTime = 0;
const unsigned long SENSOR_READ_INTERVAL = 2500;
uint8_t alarmHour = 0;
uint8_t alarmMinute = 0;
bool alarmEnabled = false;
bool isAlarmRinging = false;
unsigned long lastAlarmCheckTime = 0;


void returnToMainScreen() {
    Serial.println("DEBUG: returnToMainScreen");
    currentUIMode = UI_MODE_MAIN_SCREENS;
    currentEncoderMode = MODE_NAVIGATION;
    currentPageIndex = SCREEN_TIME;
    updateScreens();
}

void setup() {
    Serial.begin(115200);
    delay(3000); // Wait for PC to recognize USB, as per user's test code
    Serial.printf("\n--- SmartMedBox Firmware %s ---\n", FIRMWARE_VERSION);
    Serial.println("DEBUG: Starting setup().");
    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);
    pinMode(BUTTON_BACK_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin();
    u8g2.enableUTF8Print();

    // Initialize sensor BEFORE power-hungry tasks
    Serial.println("DEBUG: Initializing DHT sensor.");
    dht.begin();

    // Run hardware self-test (including motor)
    runPOST();

    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS mount failed");
        // return; // We might want to allow operation even if SPIFFS fails
    }
    Serial.println("DEBUG: SPIFFS mounted.");
    initializeHistoryFile();
    loadHistoryMetadata();
    loadPersistentStates();
    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    Serial.println("DEBUG: Rotary encoder initialized.");
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth(FIRMWARE_VERSION))/2, 45, FIRMWARE_VERSION);
    u8g2.sendBuffer();
    delay(2000);
    setupBLE();
    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    startWiFiConnection();
    updateSensorReadings();
    currentPageIndex = SCREEN_TIME;
    updateScreens();
    rotaryEncoder.setEncoderValue(currentPageIndex);
    Serial.println("--- Setup Complete ---\n");
}

void loop() {
    // Serial.println("DEBUG: loop() start"); // This is very verbose
    if (isOtaMode) {
        ArduinoOTA.handle();
        if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 500)) {
            lastBackPressTime = millis();
            playConfirmSound();
            Serial.println("DEBUG: Exiting OTA mode and rebooting.");
            u8g2.clearBuffer();
            u8g2.setFont(u8g2_font_ncenB10_tr);
            u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2, 38, "Rebooting...");
            u8g2.sendBuffer();
            delay(1000);
            ESP.restart();
        }
        return;
    }

    if (isAlarmRinging) {
        // Visual alarm indication
        if ((millis() / 200) % 2 == 0) {
            pixels.fill(pixels.Color(255, 0, 0));
        } else {
            pixels.clear();
        }
        pixels.show();

        // Stop alarm with confirm button
        if (digitalRead(BUTTON_CONFIRM_PIN) == LOW) {
            isAlarmRinging = false;
            pixels.clear();
            pixels.show();
            Serial.println("DEBUG: Alarm stopped by user.");
            Serial.println("Alarm Stopped by user.");
            delay(500); // Debounce
        }
    }

    handleWiFiConnection();
    handleHistoricDataTransfer();
    handleRealtimeData();
    updateSensorReadings();
    checkAlarm();

    handleEncoder();
    handleEncoderPush();

    if (!isAlarmRinging) {
        handleButtons();
    }

    handleBackButton();

    if (wifiState == WIFI_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
        Serial.println("DEBUG: NTP resync interval reached, forcing sync.");
        syncTimeNTPForce();
    }
    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        if (sensorDataValid) {
            // Serial.println("DEBUG: Recording history data."); // Can be verbose
            addDataToHistory(cachedTemp, cachedHum, WiFi.RSSI());
        }
    }
    if (wifiState == WIFI_CONNECTED && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        Serial.println("DEBUG: Weather update interval reached, fetching new data.");
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }
    if (millis() - lastDisplayUpdate >= displayInterval) {
        updateDisplay();
    }
    // Serial.println("DEBUG: loop() end"); // This is very verbose
}
