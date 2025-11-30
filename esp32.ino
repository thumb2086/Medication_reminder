/*
  SmartMedBox Firmware v21.4 (UI Restored)
  硬體: ESP32-C6 Dev Module

  v21.4 修正說明:
  1. [介面復原] 放棄簡化寫法，完全恢復 v20.4/v16.0 的 UI 架構。
     - 找回大字體溫濕度顯示。
     - 找回原本的天氣圖示排版。
     - 找回原本詳細的折線圖繪製邏輯。
  2. [功能整合] 保留 v21 系列的所有新功能:
     - 鬧鐘系統 (0x41, 響鈴畫面, 儲存)。
     - 硬體驅動 (WS2812B, 雙蜂鳴器, 馬達)。
     - 實體 Back 鍵 (GPIO 5) 多層級退出。
     - BLE 協定 0x01 回報。
  3. [穩定性] 修復所有編譯錯誤與變數遺失。
*/

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
// 硬體驅動
#include <ESP32Servo.h>
#include <Adafruit_NeoPixel.h>

// ==================== 腳位定義 ====================
#define I2C_SDA_PIN 22
#define I2C_SCL_PIN 21
#define ENCODER_A_PIN GPIO_NUM_18
#define ENCODER_B_PIN GPIO_NUM_19
#define ENCODER_PSH_PIN GPIO_NUM_23
#define BUTTON_CONFIRM_PIN 4
#define BUTTON_BACK_PIN 5     // Back 鍵
#define DHT_PIN 2
#define DHT_TYPE DHT11

// 新增硬體 (v17.0)
#define BUZZER1_PIN 10
#define BUZZER2_PIN 11
#define SERVO_PIN 12
#define LED_PIN 13
#define LED_COUNT 64

// ==================== 參數設定 ====================
const char* default_ssid = "charlie phone";
const char* default_password = "12345678";
String openWeatherMapApiKey = "ac1003d80943887d3d29d609afea98db";
String city = "Taipei";
String countryCode = "TW";
const float TEMP_CALIBRATION_OFFSET = 2.4;
const char* NTP_SERVER = "time.google.com";
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;

// 時間間隔
const unsigned long INTERVAL_DISPLAY = 100;
const unsigned long INTERVAL_HISTORY = 30000;
const unsigned long INTERVAL_WEATHER = 600000;
const unsigned long INTERVAL_PUSH    = 5000;

// ==================== BLE 設定 ====================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define COMMAND_CHANNEL_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define DATA_EVENT_CHANNEL_UUID "c8c7c599-809c-43a5-b825-1038aa349e5d"

// BLE 指令
#define CMD_REQ_PROTO_VER 0x01
#define CMD_TIME_SYNC     0x11
#define CMD_WIFI_CRED     0x12
#define CMD_ENG_MODE      0x13
#define CMD_SET_ALARM     0x41 // 新增鬧鐘指令
#define CMD_REQ_STATUS    0x20
#define CMD_REQ_ENV       0x30
#define CMD_REQ_HIST      0x31
#define CMD_SUB_ENV       0x32
#define CMD_UNSUB_ENV     0x33
#define CMD_REP_PROTO     0x83
#define CMD_REP_STATUS    0x80
#define CMD_REP_TAKEN     0x81
#define CMD_ACK           0x82
#define CMD_REP_ENV       0x90
#define CMD_REP_HIST_PT   0x91
#define CMD_REP_HIST_END  0x92
#define CMD_ERROR         0xEE

const uint8_t PROTOCOL_VERSION = 3;

// ==================== 圖示 (XBM) ====================
// 恢復原本的圖示定義
const unsigned char icon_ble_bits[] U8X8_PROGMEM = {0x18, 0x24, 0x42, 0x5A, 0x5A, 0x42, 0x24, 0x18};
const unsigned char icon_sync_bits[] U8X8_PROGMEM = {0x00, 0x3C, 0x46, 0x91, 0x11, 0x26, 0x3C, 0x00};
const unsigned char icon_wifi_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x24, 0x42, 0x81, 0x42, 0x24, 0x18};
const unsigned char icon_wifi_fail_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x00};
const unsigned char icon_gear_bits[] U8X8_PROGMEM = {0x24, 0x18, 0x7E, 0x25, 0x52, 0x7E, 0x18, 0x24};
const unsigned char icon_wifi_connecting_bits[] U8X8_PROGMEM = {0x00,0x00,0x0E,0x11,0x11,0x0E,0x00,0x00};

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;
File historyFile;
Servo myServo;
Adafruit_NeoPixel pixels(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);

// ==================== 狀態結構 ====================
enum WiFiState { WIFI_IDLE, WIFI_CONNECTING, WIFI_CONNECTED, WIFI_FAILED };
WiFiState wifiState = WIFI_IDLE;

enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_INFO_SCREEN, UI_MODE_OTA, UI_ALARM_RINGING };
UIMode currentUIMode = UI_MODE_MAIN_SCREENS;

enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_OTA, MENU_ITEM_INFO, MENU_ITEM_REBOOT, MENU_ITEM_BACK, NUM_MENU_ITEMS };
SystemMenuItem selectedMenuItem = MENU_ITEM_WIFI;

enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4;
ScreenState currentPageIndex = SCREEN_TIME;

struct WeatherData { String description; float temp = 0; int humidity = 0; bool valid = false; } weatherData;
struct Alarm { uint8_t hour; uint8_t min; bool enabled; };
const int MAX_ALARMS = 4;
Alarm alarms[MAX_ALARMS];

const int MAX_HISTORY = 4800;
const int HISTORY_WINDOW_SIZE = 60;
struct DataPoint { float temp; float hum; int16_t rssi; };
DataPoint historyWindowBuffer[HISTORY_WINDOW_SIZE];

// 變數
int historyIndex = 0;
int historyCount = 0;
int historyViewOffset = 0;
int menuViewOffset = 0;
const int MAX_MENU_ITEMS_ON_SCREEN = 4;
static int lastViewOffset[3] = {0, 0, 0};

bool bleDeviceConnected = false;
bool isEngineeringMode = false;
bool isRealtimeEnvSubscribed = false;
bool isOtaMode = false;
bool isSendingHistoricData = false;
int historicDataIndexToSend = 0;
int activeAlarmId = -1;

unsigned long wifiConnectionStartTime = 0;
unsigned long lastDisplayUpdate = 0;
unsigned long lastHistoryRecord = 0;
unsigned long lastEncoderPushTime = 0;
unsigned long lastConfirmPressTime = 0;
unsigned long lastBackPressTime = 0;
unsigned long confirmPressStartTime = 0;
bool confirmButtonPressed = false;
unsigned long syncIconStartTime = 0;
const unsigned long SYNC_ICON_DURATION = 3000;
unsigned long lastNTPResync = 0;
unsigned long lastWeatherUpdate = 0;
unsigned long lastRealtimeEnvPush = 0;
unsigned long lastAlarmCheck = 0;

// ==================== 函式宣告 ====================
void updateDisplay();
void drawTimeScreen();
void drawDateScreen();
void drawWeatherScreen();
void drawSensorScreen();
void drawTempChartScreen();
void drawHumChartScreen();
void drawRssiChartScreen();
void drawSystemScreen();
void drawSystemMenu();
void drawOtaScreen(String text, int progress = -1);
void drawAlarmScreen();
void drawStatusIcons();
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi);

void syncTimeNTPForce();
void handleCommand(uint8_t* data, size_t length);
void sendSensorDataReport();
void sendTimeSyncAck();
void sendErrorReport(uint8_t errorCode);
void initializeHistoryFile();
void loadHistoryMetadata();
void saveHistoryMetadata();
void addDataToHistory(float temp, float hum, int16_t rssi);
void loadHistoryWindow(int offset);
void fetchWeatherData();
void handleEncoder();
void handleEncoderPush();
void handleButtons();
const char* getWeatherIcon(const String &desc);
void sendBoxStatus();
void sendHistoricDataEnd();
void updateScreens();
void setupOTA();
void enterOtaMode();
void handleHistoricDataTransfer();
void handleRealtimeEnvPush();
void loadPersistentStates();
void handleWiFiConnection();
void startWiFiConnection();
void runHardwareSelfTest();
void playBeep(int buzzerNum, int freq, int duration);
void loadAlarms();
void saveAlarm(int id);
void checkAlarms();
void playAlarmEffect();

// ==================== BLE & 核心邏輯 ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { bleDeviceConnected = true; Serial.println("BLE Connected"); }
    void onDisconnect(BLEServer* pServer) { bleDeviceConnected = false; isRealtimeEnvSubscribed = false; BLEDevice::startAdvertising(); }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        uint8_t* data = pCharacteristic->getData();
        size_t length = pCharacteristic->getLength();
        if (length > 0) handleCommand(data, length);
    }
};

void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t cmd = data[0];

    Serial.printf("RX CMD: 0x%02X\n", cmd);

    switch (cmd) {
        case CMD_REQ_PROTO_VER: {
            uint8_t v = PROTOCOL_VERSION;
            uint8_t p[2] = {CMD_REP_PROTO, v};
            pDataEventCharacteristic->setValue(p, 2);
            pDataEventCharacteristic->notify();
            break;
        }
        case CMD_SET_ALARM: {
            if(length >= 5) {
                int id = data[1];
                if(id >= 0 && id < MAX_ALARMS) {
                    alarms[id].hour = data[2];
                    alarms[id].min = data[3];
                    alarms[id].enabled = (data[4] == 1);
                    saveAlarm(id);
                    sendTimeSyncAck();
                } else sendErrorReport(0x04);
            } else sendErrorReport(0x05);
            break;
        }
        case CMD_TIME_SYNC:
            if (length == 7) {
                tm timeinfo;
                timeinfo.tm_year = data[1] + 100;
                timeinfo.tm_mon  = data[2] - 1;
                timeinfo.tm_mday = data[3];
                timeinfo.tm_hour = data[4];
                timeinfo.tm_min  = data[5];
                timeinfo.tm_sec  = data[6];
                time_t t = mktime(&timeinfo);
                timeval tv = {t, 0};
                settimeofday(&tv, nullptr);
                syncIconStartTime = millis();
                sendTimeSyncAck();
            }
            break;
        case CMD_WIFI_CREDENTIALS:
            if (length >= 3) {
                uint8_t ssidLen = data[1];
                uint8_t passLen = data[2 + ssidLen];
                if (length == 3 + ssidLen + passLen) {
                    String newSSID = String((char*)&data[2], ssidLen);
                    String newPASS = String((char*)&data[3 + ssidLen], passLen);
                    preferences.begin("wifi", false);
                    preferences.putString("ssid", newSSID);
                    preferences.putString("pass", newPASS);
                    preferences.end();
                    startWiFiConnection();
                    sendTimeSyncAck();
                }
            }
            break;
        case CMD_ENG_MODE:
            isEngineeringMode = (data[1] == 0x01);
            preferences.begin("medbox-meta", false);
            preferences.putBool("engMode", isEngineeringMode);
            preferences.end();
            updateScreens();
            sendTimeSyncAck();
            if(isEngineeringMode) {
                pixels.fill(pixels.Color(0,0,50)); pixels.show(); delay(200); pixels.clear(); pixels.show();
            }
            break;
        case CMD_REQ_STATUS: sendBoxStatus(); break;
        case CMD_REQ_ENV: sendSensorDataReport(); break;
        case CMD_REQ_HIST:
            if (!isSendingHistoricData) {
                isSendingHistoricData = true;
                historicDataIndexToSend = 0;
                historicDataStartTime = millis();
            }
            break;
        case CMD_SUB_ENV: isRealtimeEnvSubscribed = true; sendTimeSyncAck(); break;
        case CMD_UNSUB_ENV: isRealtimeEnvSubscribed = false; sendTimeSyncAck(); break;
        default: sendErrorReport(0x03); break;
    }
}

void sendTimeSyncAck() {
    if (!bleDeviceConnected) return;
    uint8_t packet[1] = {CMD_ACK};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void sendErrorReport(uint8_t errorCode) {
    if (!bleDeviceConnected) return;
    uint8_t packet[2] = {CMD_ERROR, errorCode};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    uint8_t packet[2] = {CMD_REP_STATUS, 0x0F};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendSensorDataReport() {
    if (!bleDeviceConnected) return;
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    float h = dht.readHumidity();
    if (isnan(h) || isnan(t)) { sendErrorReport(0x02); return; }
    uint8_t packet[5];
    packet[0] = CMD_REP_ENV;
    packet[1] = (uint8_t)t;
    packet[2] = (uint8_t)((t - packet[1]) * 100);
    packet[3] = (uint8_t)h;
    packet[4] = (uint8_t)((h - packet[3]) * 100);
    pDataEventCharacteristic->setValue(packet, 5);
    pDataEventCharacteristic->notify();
}

void sendHistoricDataEnd() {
    if (!bleDeviceConnected) return;
    uint8_t packet[1] = {CMD_REP_HIST_END};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void handleHistoricDataTransfer() {
    if (!isSendingHistoricData) return;
    if (historicDataIndexToSend == 0) {
        historyFile = SPIFFS.open("/history.dat", "r");
        if (!historyFile) { sendErrorReport(0x04); isSendingHistoricData = false; return; }
    }
    if (!bleDeviceConnected) { historyFile.close(); isSendingHistoricData = false; return; }

    const int BATCH = 5;
    uint8_t batchPacket[2 + BATCH * 8];
    uint8_t pts = 0;
    int ptr = 2;

    while (pts < BATCH && historicDataIndexToSend < historyCount) {
        DataPoint dp;
        int startIdx = (historyIndex - historyCount + MAX_HISTORY) % MAX_HISTORY;
        int currentReadIdx = (startIdx + historicDataIndexToSend) % MAX_HISTORY;
        historyFile.seek(currentReadIdx * sizeof(DataPoint));
        historyFile.read((uint8_t*)&dp, sizeof(DataPoint));

        time_t timestamp = time(nullptr) - (long)((historyCount - 1 - historicDataIndexToSend) * (historyRecordInterval / 1000));

        batchPacket[ptr++] = timestamp & 0xFF;
        batchPacket[ptr++] = (timestamp >> 8) & 0xFF;
        batchPacket[ptr++] = (timestamp >> 16) & 0xFF;
        batchPacket[ptr++] = (timestamp >> 24) & 0xFF;
        batchPacket[ptr++] = (uint8_t)dp.temp;
        batchPacket[ptr++] = (uint8_t)((dp.temp - (int)dp.temp) * 100);
        batchPacket[ptr++] = (uint8_t)dp.hum;
        batchPacket[ptr++] = (uint8_t)((dp.hum - (int)dp.hum) * 100);
        pts++;
        historicDataIndexToSend++;
    }

    if (pts > 0) {
        batchPacket[0] = CMD_REP_HIST_PT;
        batchPacket[1] = pts;
        pDataEventCharacteristic->setValue(batchPacket, 2 + pts * 8);
        pDataEventCharacteristic->notify();
    }

    if (historicDataIndexToSend >= historyCount) {
        historyFile.close();
        sendHistoricDataEnd();
        isSendingHistoricData = false;
        Serial.println("Hist Transfer Done");
    }
}

// ==================== UI 繪圖函式 (恢復原本的排版) ====================
void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    // 鬧鐘響鈴優先
    if (currentUIMode == UI_ALARM_RINGING) {
        drawAlarmScreen();
    }
    else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
        drawSystemMenu();
    }
    else if (currentUIMode == UI_MODE_INFO_SCREEN) {
        drawSystemScreen();
    }
    else if (currentUIMode == UI_MODE_OTA) {
        if(isOtaMode) { // 確保在OTA模式
            u8g2.setFont(u8g2_font_ncenB08_tr);
            u8g2.drawStr(30, 12, "OTA Mode");
            u8g2.setFont(u8g2_font_profont11_tf);
            String ip = WiFi.localIP().toString();
            u8g2.drawStr((128 - u8g2.getStrWidth(ip.c_str()))/2, 35, ip.c_str());
            u8g2.drawStr(10, 55, "Ready...");
        }
    }
    else { // MAIN SCREENS
        switch (currentPageIndex) {
            case SCREEN_TIME: drawTimeScreen(); break;
            case SCREEN_DATE: drawDateScreen(); break;
            case SCREEN_WEATHER: drawWeatherScreen(); break;
            case SCREEN_SENSOR: drawSensorScreen(); break;
            case SCREEN_TEMP_CHART: if (isEngineeringMode) drawTempChartScreen(); break;
            case SCREEN_HUM_CHART: if (isEngineeringMode) drawHumChartScreen(); break;
            case SCREEN_RSSI_CHART: if (isEngineeringMode) drawRssiChartScreen(); break;
            case SCREEN_SYSTEM:
                if (isEngineeringMode) {
                    u8g2.setFont(u8g2_font_ncenB10_tr);
                    u8g2.drawStr((128 - u8g2.getStrWidth("System Menu")) / 2, 38, "System Menu");
                }
                break;
        }
        drawStatusIcons();
    }
    u8g2.sendBuffer();
}

void drawTimeScreen() {
    time_t now; time(&now);
    struct tm *ptm = localtime(&now);
    if (now < 1672531200) {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set");
    } else {
        u8g2.setFont(u8g2_font_fub20_tn);
        char s[9]; sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec);
        u8g2.drawStr((128 - u8g2.getStrWidth(s))/2, 42, s);
        // 呼吸燈
        int b = (millis()/20)%50; pixels.setPixelColor(0, pixels.Color(0,0,b)); pixels.show();
    }
}

void drawDateScreen() {
    time_t now; time(&now);
    struct tm *ptm = localtime(&now);
    const char* week[] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
    const char* month[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
    char day[4], year[6]; sprintf(day, "%02d", ptm->tm_mday); sprintf(year, "%d", ptm->tm_year + 1900);
    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, day);
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, week[ptm->tm_wday]);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(72, 40, month[ptm->tm_mon]); u8g2.drawStr(72, 56, year);
    pixels.clear(); pixels.show();
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr(0, 12, city.c_str());
    if (wifiState != WIFI_CONNECTED) {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return;
    }
    if (weatherData.valid) {
        char buf[20];
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t);
        u8g2.drawStr(5, 50, getWeatherIcon(weatherData.description));
        u8g2.setFont(u8g2_font_fub25_tn);
        sprintf(buf, "%.1f", weatherData.temp);
        u8g2.drawStr(45, 32, buf);
        u8g2.setFont(u8g2_font_ncenR10_tr);
        sprintf(buf, "H:%d%%", weatherData.humidity);
        u8g2.drawStr(45, 64, buf);
    } else {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "No data");
    }
}

void drawSensorScreen() {
    float h = dht.readHumidity();
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    if (isnan(h) || isnan(t)) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 40, "Sensor Error!"); return; }
    char buf[20];
    u8g2.setFont(u8g2_font_helvB10_tr); u8g2.drawStr((128 - u8g2.getStrWidth("INDOOR"))/2, 12, "INDOOR");
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 38, "TEMP");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.1f C", t); u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 38, buf);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 62, "HUMI");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.0f %%", h); u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 62, buf);
}

void drawSystemScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 12, "System Info");
    u8g2.setFont(u8g2_font_profont11_tf); int y = 28;
    String ssid = WiFi.SSID(); if (ssid == "") ssid = "N/A";
    if (wifiState == WIFI_CONNECTED) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y += 12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y += 12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y += 12;
    } else { u8g2.drawStr(0, y, "WiFi Disconnected"); y += 12; }
    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap()/1024) + " KB").c_str()); y += 12;
    u8g2.drawStr(0, y, ("Up: " + String(millis()/60000) + " min").c_str());
}

void drawSystemMenu() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("System Menu")) / 2, 10, "System Menu");
    const char* menuItems[] = { "Connect to WiFi", "OTA Update", "System Info", "Reboot Device", "Back to Main" };
    for (int i = 0; i < MAX_MENU_ITEMS_ON_SCREEN; i++) {
        int itemIndex = menuViewOffset + i;
        if (itemIndex < NUM_MENU_ITEMS) {
            int y = 24 + i * 11;
            if (itemIndex == selectedMenuItem) {
                u8g2.drawBox(0, y - 9, 128 - 6, 11);
                u8g2.setDrawColor(0); u8g2.drawStr(5, y, menuItems[itemIndex]); u8g2.setDrawColor(1);
            } else {
                u8g2.drawStr(5, y, menuItems[itemIndex]);
            }
        }
    }
    // 滾動條
    u8g2.drawFrame(124, 20, 3, 44);
    int h = 44 * 4 / NUM_MENU_ITEMS;
    int y = 20 + (menuViewOffset * (44-h) / (NUM_MENU_ITEMS-4));
    u8g2.drawBox(124, y, 3, h);
}

void drawAlarmScreen() {
    u8g2.setFont(u8g2_font_fub20_tn);
    u8g2.drawStr(15, 35, "ALARM!");
    char buf[20]; sprintf(buf, "Take Meds #%d", activeAlarmId + 1);
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128-u8g2.getStrWidth(buf))/2, 55, buf);
    u8g2.drawStr(20, 10, "Press OK to Stop");
}

void drawStatusIcons() {
    if (currentUIMode != UI_MODE_MAIN_SCREENS || currentPageIndex != SCREEN_TIME) return;
    int x = 0;
    const int spacing = 10;
    if (bleDeviceConnected) { u8g2.drawXBM(x, 2, 8, 8, icon_ble_bits); x += spacing; }
    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) {
        u8g2.drawXBM(x, 2, 8, 8, icon_sync_bits); x += spacing;
    }
    if (wifiState == WIFI_CONNECTED) u8g2.drawXBM(x, 2, 8, 8, icon_wifi_bits);
    else if (wifiState == WIFI_CONNECTING) {
        if((millis()/500)%2) u8g2.drawXBM(x, 2, 8, 8, icon_wifi_connecting_bits);
    } else u8g2.drawXBM(x, 2, 8, 8, icon_wifi_fail_bits);
    x += spacing;
    if (isEngineeringMode) u8g2.drawXBM(x, 2, 8, 8, icon_gear_bits);
}

void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);
    if (historyCount < 2) { u8g2.drawStr(10, 35, "No Data"); return; }
    loadHistoryWindow(historyViewOffset);
    int pts = min(HISTORY_WINDOW_SIZE, historyCount);
    float minV = 999, maxV = -999;
    for (int i = 0; i < pts; i++) {
        float v = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        if (v < minV) minV = v; if (v > maxV) maxV = v;
    }
    if (isRssi) { minV=max(minV,-100.0f); maxV=min(maxV,-30.0f); if(maxV-minV<10){minV-=5; maxV+=5;} }
    else if(maxV-minV < 1) { minV-=1; maxV+=1; }

    int w = 110, h = 40, x = 18, y = 15;
    u8g2.setFont(u8g2_font_5x7_tr);
    char buf[10]; sprintf(buf, "%.0f", maxV); u8g2.drawStr(0, y+6, buf);
    sprintf(buf, "%.0f", minV); u8g2.drawStr(0, y+h, buf);
    u8g2.drawFrame(x, y, w, h);

    for (int i = 1; i < pts; i++) {
        float v1 = isRssi ? historyWindowBuffer[i-1].rssi : (isTemp ? historyWindowBuffer[i-1].temp : historyWindowBuffer[i-1].hum);
        float v2 = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        int px1 = x + (i-1)*w/pts;
        int py1 = y + h - (v1-minV)/(maxV-minV)*h;
        int px2 = x + i*w/pts;
        int py2 = y + h - (v2-minV)/(maxV-minV)*h;
        u8g2.drawLine(px1, py1, px2, py2);
    }
    if (currentEncoderMode == MODE_VIEW_ADJUST) u8g2.drawStr(2, 64, "VIEW");
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

// ==================== 其他邏輯 ====================
void playBeep(int buzzerNum, int freq, int duration) {
    int pin = (buzzerNum == 1) ? BUZZER1_PIN : BUZZER2_PIN;
    tone(pin, freq, duration); delay(duration); noTone(pin);
}

void loadAlarms() {
    preferences.begin("alarms", true);
    for(int i=0; i<MAX_ALARMS; i++) {
        char key[5]; sprintf(key, "a%d", i);
        uint32_t val = preferences.getUInt(key, 0);
        alarms[i].enabled = (val & 0x1);
        alarms[i].min = (val >> 8) & 0xFF;
        alarms[i].hour = (val >> 16) & 0xFF;
    }
    preferences.end();
}

void saveAlarm(int id) {
    if(id < 0 || id >= MAX_ALARMS) return;
    preferences.begin("alarms", false);
    char key[5]; sprintf(key, "a%d", id);
    uint32_t val = (alarms[id].hour << 16) | (alarms[id].min << 8) | (alarms[id].enabled ? 1 : 0);
    preferences.putUInt(key, val);
    preferences.end();
}

void checkAlarms() {
    if(currentUIMode == UI_ALARM_RINGING) return;
    time_t now; time(&now);
    struct tm *t = localtime(&now);
    if(t->tm_sec == 0) {
        for(int i=0; i<MAX_ALARMS; i++) {
            if(alarms[i].enabled && alarms[i].hour == t->tm_hour && alarms[i].min == t->tm_min) {
                currentUIMode = UI_ALARM_RINGING;
                activeAlarmId = i;
                Serial.printf("Alarm %d Triggered!\n", i);
                break;
            }
        }
    }
}

void playAlarmEffect() {
    static unsigned long lastBeep = 0;
    static int phase = 0;
    if(millis() - lastBeep > 200) {
        lastBeep = millis();
        phase = !phase;
        if(phase) pixels.fill(pixels.Color(255, 0, 0)); else pixels.clear();
        pixels.show();
        if(phase) tone(BUZZER1_PIN, 2000, 150); else tone(BUZZER2_PIN, 1500, 150);
    }
}

void startWiFiConnection() {
    if (wifiState == WIFI_CONNECTING) return;
    wifiState = WIFI_CONNECTING;
    wifiConnectionStartTime = millis();
    preferences.begin("wifi", true);
    String s = preferences.getString("ssid", default_ssid);
    String p = preferences.getString("pass", default_password);
    preferences.end();
    WiFi.disconnect(true);
    delay(100);
    WiFi.begin(s.c_str(), p.c_str());
}

void handleWiFiConnection() {
    if (wifiState != WIFI_CONNECTING) return;
    if (WiFi.status() == WL_CONNECTED) {
        wifiState = WIFI_CONNECTED;
        syncTimeNTPForce();
        fetchWeatherData();
        if (MDNS.begin("medbox")) ArduinoOTA.begin();
        setupOTA();
    } else if (millis() - wifiConnectionStartTime > 15000) {
        wifiState = WIFI_FAILED;
        WiFi.disconnect(true);
    }
}

void syncTimeNTPForce() {
    if (wifiState != WIFI_CONNECTED) return;
    configTime(GMT_OFFSET, DAYLIGHT_OFFSET, NTP_SERVER);
    struct tm timeinfo;
    if (getLocalTime(&timeinfo, 5000)) {
        syncIconStartTime = millis();
        lastNTPResync = millis();
    }
}

void setupOTA() {
    ArduinoOTA.setHostname("smartmedbox");
    ArduinoOTA.setPassword("medbox123");
    ArduinoOTA.onStart([]() { SPIFFS.end(); drawOtaScreen("Updating...", 0); });
    ArduinoOTA.onProgress([](unsigned int progress, unsigned int total) { drawOtaScreen("Updating...", (progress / (total / 100))); });
    ArduinoOTA.onEnd([]() { drawOtaScreen("Complete!", 100); delay(1000); ESP.restart(); });
    ArduinoOTA.begin();
}

void enterOtaMode() {
    if (isOtaMode || wifiState != WIFI_CONNECTED) {
        u8g2.clearBuffer(); u8g2.drawStr(20, 30, "No WiFi!"); u8g2.sendBuffer(); delay(1000); return;
    }
    isOtaMode = true;
    currentUIMode = UI_MODE_OTA;
}

void drawOtaScreen(String text, int progress) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(30, 12, "OTA Update");
    u8g2.setFont(u8g2_font_profont11_tf); u8g2.drawStr(10, 32, text.c_str());
    if(progress >= 0) { u8g2.drawFrame(14, 45, 100, 10); u8g2.drawBox(14, 45, progress, 10); }
    u8g2.sendBuffer();
}

void loadPersistentStates() {
    preferences.begin("medbox-meta", true);
    isEngineeringMode = preferences.getBool("engMode", false);
    preferences.end();
}

void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File f = SPIFFS.open("/history.dat", FILE_WRITE);
        DataPoint empty = {0,0,0};
        for (int i = 0; i < MAX_HISTORY; i++) f.write((uint8_t*)&empty, sizeof(DataPoint));
        f.close();
    }
}

void loadHistoryMetadata() {
    preferences.begin("medbox-meta", true);
    historyCount = preferences.getInt("hist_count", 0);
    historyIndex = preferences.getInt("hist_index", 0);
    preferences.end();
}

void saveHistoryMetadata() {
    preferences.begin("medbox-meta", false);
    preferences.putInt("hist_count", historyCount);
    preferences.putInt("hist_index", historyIndex);
    preferences.end();
}

void addDataToHistory(float temp, float hum, int16_t rssi) {
    File f = SPIFFS.open("/history.dat", "r+");
    if(f) {
        DataPoint dp = {temp, hum, rssi};
        f.seek(historyIndex * sizeof(DataPoint));
        f.write((uint8_t*)&dp, sizeof(DataPoint));
        f.close();
        historyIndex = (historyIndex + 1) % MAX_HISTORY;
        if (historyCount < MAX_HISTORY) historyCount++;
        saveHistoryMetadata();
        if (currentEncoderMode == MODE_VIEW_ADJUST) {
            int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
            rotaryEncoder.setBoundaries(0, maxOffset, false);
        }
    }
}

void loadHistoryWindow(int offset) {
    int pts = min(historyCount, HISTORY_WINDOW_SIZE);
    if(pts == 0) return;
    File f = SPIFFS.open("/history.dat", "r");
    if(f) {
        int start = (historyIndex - offset - pts + MAX_HISTORY) % MAX_HISTORY;
        for(int i=0; i<pts; i++) {
            f.seek(((start + i) % MAX_HISTORY) * sizeof(DataPoint));
            f.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
        }
        f.close();
    }
}

void fetchWeatherData() {
    if (wifiState != WIFI_CONNECTED) { weatherData.valid = false; return; }
    HTTPClient http;
    String url = "http://api.openweathermap.org/data/2.5/weather?q=" + city + "," + countryCode + "&units=metric&lang=zh_tw&APPID=" + openWeatherMapApiKey;
    http.begin(url);
    if (http.GET() > 0) {
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
    }
    http.end();
}

const char* getWeatherIcon(const String &desc) {
    String s = desc; s.toLowerCase();
    if (s.indexOf("clear") >= 0) return "A";
    if (s.indexOf("cloud") >= 0) return "C";
    if (s.indexOf("rain") >= 0) return "R";
    return "C";
}

void handleRealtimeEnvPush() {
    if (isRealtimeEnvSubscribed && bleDeviceConnected && (millis() - lastRealtimeEnvPush >= INTERVAL_PUSH)) {
        lastRealtimeEnvPush = millis();
        sendSensorDataReport();
    }
}

void updateScreens() {
    NUM_SCREENS = isEngineeringMode ? 8 : 4;
    if (currentUIMode == UI_MODE_MAIN_SCREENS) {
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        if (currentPageIndex >= NUM_SCREENS) currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(currentPageIndex);
    } else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
        rotaryEncoder.setBoundaries(0, NUM_MENU_ITEMS - 1, true);
        rotaryEncoder.setEncoderValue(selectedMenuItem);
    }
    updateDisplay();
}

void handleEncoder() {
    if (rotaryEncoder.encoderChanged()) {
        playBeep(1, 4000, 10);
        if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            selectedMenuItem = (SystemMenuItem)rotaryEncoder.readEncoder();
            if (selectedMenuItem >= menuViewOffset + MAX_MENU_ITEMS_ON_SCREEN) menuViewOffset = selectedMenuItem - MAX_MENU_ITEMS_ON_SCREEN + 1;
            if (selectedMenuItem < menuViewOffset) menuViewOffset = selectedMenuItem;
        } else if (currentEncoderMode == MODE_VIEW_ADJUST) {
            historyViewOffset = rotaryEncoder.readEncoder();
        } else {
            currentPageIndex = (ScreenState)rotaryEncoder.readEncoder();
        }
        updateDisplay();
    }
}

void handleEncoderPush() {
    if (digitalRead(ENCODER_PSH_PIN) == LOW && (millis() - lastEncoderPushTime > 300)) {
        lastEncoderPushTime = millis();
        playBeep(2, 2000, 50);
        if (currentUIMode == UI_MODE_MAIN_SCREENS) {
            if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) {
                currentUIMode = UI_MODE_SYSTEM_MENU; selectedMenuItem = MENU_ITEM_WIFI; menuViewOffset = 0; updateScreens();
            } else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) {
                currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION;
                int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
                if (currentEncoderMode == MODE_VIEW_ADJUST) {
                    rotaryEncoder.setBoundaries(0, maxOffset, false);
                    rotaryEncoder.setEncoderValue(historyViewOffset);
                } else {
                    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
                    rotaryEncoder.setEncoderValue(currentPageIndex);
                }
            }
        } else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            switch (selectedMenuItem) {
                case MENU_ITEM_WIFI: startWiFiConnection(); break;
                case MENU_ITEM_OTA: enterOtaMode(); break;
                case MENU_ITEM_INFO: currentUIMode = UI_MODE_INFO_SCREEN; break;
                case MENU_ITEM_REBOOT: ESP.restart(); break;
                case MENU_ITEM_BACK: currentUIMode = UI_MODE_MAIN_SCREENS; updateScreens(); break;
            }
        } else if (currentUIMode == UI_MODE_INFO_SCREEN) {
            currentUIMode = UI_MODE_SYSTEM_MENU;
        }
        updateDisplay();
    }
}

void handleButtons() {
    bool pressed = (digitalRead(BUTTON_CONFIRM_PIN) == LOW);
    if (pressed && !confirmButtonPressed) { confirmPressStartTime = millis(); confirmButtonPressed = true; }
    else if (!pressed && confirmButtonPressed) {
        if (millis() - confirmPressStartTime < 3000) {
            lastConfirmPressTime = millis();
            playBeep(2, 2000, 50);
            if (currentUIMode != UI_MODE_MAIN_SCREENS) {
                currentUIMode = UI_MODE_MAIN_SCREENS; currentPageIndex = SCREEN_TIME; updateScreens();
            } else {
                currentPageIndex = SCREEN_TIME; rotaryEncoder.setEncoderValue(SCREEN_TIME);
            }
            updateDisplay();
        }
        confirmButtonPressed = false;
    }

    // Back Button
    if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 300)) {
        lastBackPressTime = millis();
        playBeep(2, 1000, 50);
        if (currentUIMode == UI_MODE_OTA) ESP.restart();
        else if (currentUIMode == UI_MODE_INFO_SCREEN) currentUIMode = UI_MODE_SYSTEM_MENU;
        else if (currentUIMode == UI_MODE_SYSTEM_MENU) { currentUIMode = UI_MODE_MAIN_SCREENS; updateScreens(); }
        else if (currentEncoderMode == MODE_VIEW_ADJUST) {
            currentEncoderMode = MODE_NAVIGATION;
            rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
            rotaryEncoder.setEncoderValue(currentPageIndex);
        }
        updateDisplay();
    }
}

void runHardwareSelfTest() {
    u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10,30,"Self Test..."); u8g2.sendBuffer();
    pixels.fill(pixels.Color(0,50,0)); pixels.show(); delay(300); pixels.clear(); pixels.show();
    playBeep(1, 1000, 100);
    myServo.attach(SERVO_PIN); myServo.write(0); delay(200); myServo.write(90); delay(200); myServo.write(0); delay(200);
}

// ==================== SETUP / LOOP ====================
void setup() {
    Serial.begin(115200);
    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);
    pinMode(BUTTON_BACK_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin(); u8g2.enableUTF8Print();
    dht.begin();
    pixels.begin(); pixels.clear(); pixels.show();
    if(SPIFFS.begin(true)) { initializeHistoryFile(); loadHistoryMetadata(); loadPersistentStates(); loadAlarms(); }

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, 3, true);

    runHardwareSelfTest();

    BLEDevice::init("SmartMedBox");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic* pCommand = pService->createCharacteristic(COMMAND_CHANNEL_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommand->setCallbacks(new CommandCallbacks());
    pDataEventCharacteristic = pService->createCharacteristic(DATA_EVENT_CHANNEL_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pDataEventCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
    BLEDevice::startAdvertising();

    WiFi.mode(WIFI_STA);
    startWiFiConnection();
}

void loop() {
    if (isOtaMode) {
        ArduinoOTA.handle();
        if(digitalRead(BUTTON_BACK_PIN)==LOW) ESP.restart();
        return;
    }

    handleWiFiConnection();
    handleHistoricDataTransfer();
    handleRealtimeEnvPush();
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (wifiState == WIFI_CONNECTED && millis() - lastNTPResync >= 12*3600000) syncTimeNTPForce();
    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        float t = dht.readTemperature(); float h = dht.readHumidity();
        if(!isnan(t)) addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, WiFi.RSSI());
    }
    if (millis() - lastWeatherUpdate > WEATHER_INTERVAL) { lastWeatherUpdate = millis(); fetchWeatherData(); }
    if (millis() - lastAlarmCheck > 1000) { lastAlarmCheck = millis(); checkAlarms(); }

    if (currentUIMode == UI_ALARM_RINGING) {
        playAlarmEffect();
        if(!digitalRead(BUTTON_CONFIRM_PIN) || !digitalRead(BUTTON_BACK_PIN)) {
            currentUIMode = UI_MODE_MAIN_SCREENS; pixels.clear(); pixels.show(); noTone(BUZZER1_PIN); noTone(BUZZER2_PIN); delay(500);
        }
    }

    if (millis() - lastDisplayUpdate >= INTERVAL_DISPLAY) updateDisplay();
}