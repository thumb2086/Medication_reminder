
#pragma once

#include <Arduino.h>
#include <U8g2lib.h>
#include <AiEsp32RotaryEncoder.h>
#include <DHT.h>
#include <BLECharacteristic.h>
#include <Preferences.h>
#include "SPIFFS.h"
// #include <ESP32Servo.h> // Removed, using native LEDC
#include <Adafruit_NeoPixel.h>
#include "config.h"

// ==================== 全域物件 ====================

extern U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2;
extern AiEsp32RotaryEncoder rotaryEncoder;
extern DHT dht;
extern BLECharacteristic *pDataEventCharacteristic;
extern Preferences preferences;
extern File historyFile;
// extern Servo sg90; // Removed, using native LEDC
extern Adafruit_NeoPixel pixels;

// ==================== 狀態與數據 ====================

enum WiFiState { WIFI_IDLE, WIFI_CONNECTING, WIFI_CONNECTED, WIFI_FAILED };
extern WiFiState wifiState;
extern unsigned long wifiConnectionStartTime; // <-- Re-added this line
enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_INFO_SCREEN };
extern UIMode currentUIMode;
enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_OTA, MENU_ITEM_INFO, MENU_ITEM_REBOOT, MENU_ITEM_BACK, NUM_MENU_ITEMS };
extern SystemMenuItem selectedMenuItem;
extern int menuViewOffset;
extern const int MAX_MENU_ITEMS_ON_SCREEN;
enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
extern EncoderMode currentEncoderMode;
enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
extern int NUM_SCREENS;
extern ScreenState currentPageIndex;
struct WeatherData { String description; float temp = 0; int humidity = 0; bool valid = false; };
extern WeatherData weatherData;

struct DataPoint { float temp; float hum; int16_t rssi; };
extern DataPoint historyWindowBuffer[HISTORY_WINDOW_SIZE];
extern int historyIndex;
extern int historyCount;
extern int historyViewOffset;

extern bool bleDeviceConnected;
extern bool isEngineeringMode;
extern bool isOtaMode;
extern bool isSendingHistoricData; // <-- Corrected definition
extern int historicDataIndexToSend; // <-- Corrected definition
extern unsigned long historicDataStartTime; // <-- Corrected definition

extern unsigned long lastDisplayUpdate;
extern const unsigned long displayInterval;
extern unsigned long lastHistoryRecord;
extern const unsigned long historyRecordInterval;
extern unsigned long lastEncoderPushTime;
extern unsigned long lastConfirmPressTime;
extern unsigned long confirmPressStartTime;
extern bool confirmButtonPressed;
extern unsigned long syncIconStartTime;
extern const unsigned long SYNC_ICON_DURATION;
extern unsigned long lastNTPResync;
extern const unsigned long NTP_RESYNC_INTERVAL;
extern unsigned long lastWeatherUpdate;
extern const unsigned long WEATHER_INTERVAL;
extern unsigned long lastBackPressTime;

extern bool isRealtimeEnabled;
extern unsigned long lastRealtimeSend;
extern const unsigned long REALTIME_INTERVAL;

extern float cachedTemp;
extern float cachedHum;
extern bool sensorDataValid;
extern unsigned long lastSensorReadTime;
extern const unsigned long SENSOR_READ_INTERVAL;

extern uint8_t alarmHour; // <-- Corrected definition
extern uint8_t alarmMinute; // <-- Corrected definition
extern bool alarmEnabled; // <-- Corrected definition
extern bool isAlarmRinging; // <-- Corrected definition
extern unsigned long lastAlarmCheckTime; // <-- Corrected definition


// ==================== 函式宣告 (Prototypes) ====================

// ble_handler.cpp
void setupBLE();
void handleCommand(uint8_t* data, size_t length);
void sendBoxStatus();
void sendMedicationTaken(uint8_t slot);
void sendSensorDataReport();
void sendRealtimeSensorData();
void sendHistoricDataEnd();
void sendTimeSyncAck();
void sendErrorReport(uint8_t errorCode);
void handleHistoricDataTransfer();
void handleRealtimeData();

// display.cpp
void updateDisplay();
void drawStatusIcons();
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi);
void drawTimeScreen();
void drawDateScreen();
void drawWeatherScreen();
void drawSensorScreen();
void drawTempChartScreen();
void drawHumChartScreen();
void drawRssiChartScreen();
void drawSystemScreen();
void drawSystemMenu();
void drawOtaScreen(String text, int progress);
void updateScreens();

// hardware.cpp
void runPOST();
void playTickSound();
void playConfirmSound();
void updateSensorReadings();
void checkAlarm();

// input.cpp
void handleEncoder();
void handleEncoderPush();
void handleButtons();
void handleBackButton();

// storage.cpp
void initializeHistoryFile();
void loadHistoryMetadata();
void addDataToHistory(float temp, float hum, int16_t rssi);
void loadHistoryWindow(int offset);
void loadPersistentStates();

// wifi_ota.cpp
void setupOTA();
void enterOtaMode();
void handleWiFiConnection();
void startWiFiConnection();
void syncTimeNTPForce();
void fetchWeatherData();

// main (in esp32.ino)
void returnToMainScreen();
const char* getWeatherIcon(const String &desc);
