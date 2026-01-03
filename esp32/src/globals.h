#pragma once

#include <Arduino.h>
#include <U8g2lib.h>
#include <AiEsp32RotaryEncoder.h>
#include <DHT.h>
#include <BLECharacteristic.h>
#include <Preferences.h>
#include "SPIFFS.h"
#include <Adafruit_NeoPixel.h>

// ==================== 列舉 (Enums) ====================
enum WiFiState { WIFI_IDLE, WIFI_CONNECTING, WIFI_CONNECTED, WIFI_FAILED };
enum ScreenState { SCREEN_TIME, SCREEN_WEATHER, SCREEN_HISTORY_CHART, SCREEN_PILL_STATUS };
enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_HISTORY_VIEW };
enum EncoderMode { MODE_NAVIGATION, MODE_VALUE_CHANGE, MODE_MENU_SELECTION, MODE_HISTORY_SCROLL };
enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_OTA, MENU_ITEM_INFO, MENU_ITEM_REBOOT };

// ==================== 結構 (Structs) ====================
struct WeatherData {
    String description;
    float temp;
    int humidity;
    bool isValid = false;
};

struct DataPoint {
    float temp;
    float hum;
    long rssi;
};

// ==================== 全域物件宣告 ====================
extern U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2;
extern AiEsp32RotaryEncoder rotaryEncoder;
extern DHT dht;
extern Adafruit_NeoPixel pixels;
extern BLECharacteristic *pDataEventCharacteristic;
extern Preferences preferences;
extern File historyFile;

// ==================== 全域變數宣告 ====================
extern WiFiState wifiState;
extern unsigned long wifiConnectionStartTime;
extern UIMode currentUIMode;
extern SystemMenuItem selectedMenuItem;
extern int menuViewOffset;
extern const int MAX_MENU_ITEMS_ON_SCREEN;
extern EncoderMode currentEncoderMode;
extern int NUM_SCREENS;
extern ScreenState currentPageIndex;
extern WeatherData weatherData;
extern DataPoint historyWindowBuffer[60];
extern int historyIndex;
extern int historyCount;
extern int historyViewOffset;
extern bool bleDeviceConnected;
extern bool isEngineeringMode;

// --- OTA & System State ---
extern bool isOtaMode;
extern bool isBleOtaInProgress;
extern unsigned long otaStartTime;
extern size_t otaTotalSize;
extern size_t otaBytesReceived;

// --- Data & Sync State ---
extern bool isSendingHistoricData;
extern int historicDataIndexToSend;
extern unsigned long historicDataStartTime;
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

// --- Alarm State ---
extern uint8_t alarmHour;
extern uint8_t alarmMinute;
extern bool alarmEnabled;
extern bool isAlarmRinging;
extern unsigned long lastAlarmCheckTime;

// ==================== 函式原型宣告 ====================
// (Functions defined in .ino or other .cpp files)
void startWiFiConnection();
void syncTimeNTPForce();
void fetchWeatherData();
void updateScreens();
void playConfirmSound();
void runPOST();
void addDataToHistory(float temp, float hum, long rssi);
void initializeHistoryFile();
void loadHistoryMetadata();
void loadPersistentStates();
void updateSensorReadings();
void checkAlarm();
void handleWiFiConnection();
void handleHistoricDataTransfer();
void handleRealtimeData();
void handleEncoder();
void handleEncoderPush();
void handleButtons();
void handleBackButton();
void updateDisplay();
void setupBLE();
void handleCommand(uint8_t* data, size_t length);
void sendTimeSyncAck();
void sendErrorReport(uint8_t errorCode);
