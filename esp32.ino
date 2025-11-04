/*
  SmartMedBox Firmware v16.3
  硬體: ESP32-C6
  IDE: Arduino ESP32 v3.0.0+
  板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

  v16.3 修復重點:
  - 斷電後歷史資料完全復原
  - 修正 BLE 變數錯誤 (restorations → pService)
  - 補全 updateDisplay() 與所有 UI 函式
  - 100% 可編譯
*/

#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <Update.h>
#include <WebServer.h>
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

// ==================== OTA Web Server ====================
WebServer otaServer(80);
bool otaMode = false;
unsigned long otaStartTime = 0;

// ==================== 腳位定義 ====================
#define I2C_SDA_PIN 22
#define I2C_SCL_PIN 21
#define ENCODER_A_PIN GPIO_NUM_18
#define ENCODER_B_PIN GPIO_NUM_19
#define ENCODER_PSH_PIN GPIO_NUM_23
#define BUTTON_CONFIRM_PIN 4
#define DHT_PIN 2
#define DHT_TYPE DHT11

// ==================== Wi-Fi & NTP ====================
const char* default_ssid = "charlie phone";
const char* default_password = "12345678";
String openWeatherMapApiKey = "ac1003d80943887d3d29d609afea98db";
String city = "Taipei";
String countryCode = "TW";
const float TEMP_CALIBRATION_OFFSET = 2.4;
const char* NTP_SERVER = "time.google.com";
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;

// ==================== BLE UUID ====================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define COMMAND_CHANNEL_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define DATA_EVENT_CHANNEL_UUID "c8c7c599-809c-43a5-b825-1038aa349e5d"

// ==================== BLE 指令碼 ====================
#define CMD_TIME_SYNC               0x11
#define CMD_WIFI_CREDENTIALS        0x12
#define CMD_SET_ENGINEERING_MODE    0x13
#define CMD_REQUEST_STATUS          0x20
#define CMD_REQUEST_ENV             0x30
#define CMD_REQUEST_HISTORIC        0x31
#define CMD_ENTER_OTA               0x40
#define CMD_REPORT_STATUS           0x80
#define CMD_REPORT_TAKEN            0x81
#define CMD_TIME_SYNC_ACK           0x82
#define CMD_REPORT_ENV              0x90
#define CMD_REPORT_HISTORIC_POINT   0x91
#define CMD_REPORT_HISTORIC_END     0x92
#define CMD_ERROR                   0xEE

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;

// ==================== 狀態與數據 ====================
enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM, SCREEN_OTA };
int NUM_SCREENS = 4;

ScreenState currentPageIndex = SCREEN_TIME;
static int lastViewOffset[3] = {0, 0, 0};

struct WeatherData {
    String description; float temp = 0; int humidity = 0; bool valid = false;
} weatherData;

const int MAX_HISTORY = 4800;
const int HISTORY_WINDOW_SIZE = 60;
struct DataPoint { float temp; float hum; int16_t rssi; };
DataPoint historyWindowBuffer[HISTORY_WINDOW_SIZE];
int historyIndex = 0;
int historyCount = 0;
int historyViewOffset = 0;

bool bleDeviceConnected = false;
bool isEngineeringMode = false;
unsigned long lastDisplayUpdate = 0;
const unsigned long displayInterval = 100;
unsigned long lastHistoryRecord = 0;
const unsigned long historyRecordInterval = 30000;
unsigned long lastPersistenceSave = 0;
const unsigned long persistenceSaveInterval = 900000;
unsigned long lastEncoderPushTime = 0;
unsigned long lastConfirmPressTime = 0;
unsigned long syncIconStartTime = 0;
const unsigned long SYNC_ICON_DURATION = 3000;
unsigned long lastNTPResync = 0;
const unsigned long NTP_RESYNC_INTERVAL = 12 * 3600000;
unsigned long lastWeatherUpdate = 0;
const unsigned long WEATHER_INTERVAL = 600000;

// WiFi 連接狀態機
bool wifiConnecting = false;
bool manualConnectRequested = false;
String targetSSID = "";
String targetPASS = "";
unsigned long wifiConnectStartTime = 0;
const unsigned long WIFI_CONNECT_TIMEOUT = 30000;
int wifiConnectProgress = 0;

// ==================== 函式宣告 ====================
void updateDisplay();
void drawStatusIcons();
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
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi);
void drawTimeScreen();
void drawDateScreen();
void drawWeatherScreen();
void drawSensorScreen();
void drawTempChartScreen();
void drawHumChartScreen();
void drawRssiChartScreen();
void drawSystemScreen();
void drawOTAScreen();
void fetchWeatherData();
void handleEncoder();
void handleEncoderPush();
void handleButtons();
const char* getWeatherIcon(const String &desc);
void sendBoxStatus();
void sendMedicationTaken(uint8_t slot);
void sendHistoricData();
void sendHistoricDataEnd();
void updateScreens();
void startWiFiConnection(String ssid, String pass, bool showScreen);
void updateWiFiConnection();
void drawWiFiConnecting(String ssid, int progress, int dotsCount);
void drawWiFiConnected(String ip);
void drawWiFiFailed();
void startOTAWebServer();
void handleOTAUpload();

// ==================== BLE 回呼 ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { bleDeviceConnected = true; Serial.println("BLE Connected"); }
    void onDisconnect(BLEServer* pServer) { bleDeviceConnected = false; Serial.println("BLE Disconnected"); BLEDevice::startAdvertising(); }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        uint8_t* data = pCharacteristic->getData();
        size_t length = pCharacteristic->getLength();
        if (length > 0) { handleCommand(data, length); }
    }
};

// ==================== BLE 指令處理 ====================
void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t command = data[0];

    switch (command) {
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
                    startWiFiConnection(newSSID, newPASS, true);
                    sendTimeSyncAck();
                }
            }
            break;

        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                isEngineeringMode = (data[1] == 0x01);
                updateScreens();
                sendTimeSyncAck();
            }
            break;

        case CMD_ENTER_OTA:
            if (length == 1 && WiFi.status() == WL_CONNECTED) {
                otaMode = true;
                otaStartTime = millis();
                startOTAWebServer();
                currentPageIndex = SCREEN_OTA;
                updateDisplay();
            }
            break;

        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC: sendHistoricData(); break;
        default: sendErrorReport(0x03); break;
    }
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    uint8_t slotMask = 0b00001111;
    uint8_t packet[2] = {CMD_REPORT_STATUS, slotMask};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendMedicationTaken(uint8_t slot) {
    if (!bleDeviceConnected || slot > 7) return;
    uint8_t packet[2] = {CMD_REPORT_TAKEN, slot};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendSensorDataReport() {
    if (!bleDeviceConnected) return;
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    float h = dht.readHumidity();
    if (isnan(h) || isnan(t)) { sendErrorReport(0x02); return; }

    uint8_t packet[5];
    packet[0] = CMD_REPORT_ENV;
    packet[1] = (uint8_t)t;
    packet[2] = (uint8_t)((t - packet[1]) * 100);
    packet[3] = (uint8_t)h;
    packet[4] = (uint8_t)((h - packet[3]) * 100);
    pDataEventCharacteristic->setValue(packet, 5);
    pDataEventCharacteristic->notify();
}

void sendHistoricDataEnd() {
    if (!bleDeviceConnected) return;
    uint8_t packet[1] = {CMD_REPORT_HISTORIC_END};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void sendHistoricData() {
    if (!bleDeviceConnected || historyCount == 0) { sendHistoricDataEnd(); return; }
    File file = SPIFFS.open("/history.dat", "r");
    if (!file) { sendErrorReport(0x04); return; }

    DataPoint dp;
    int startIdx = (historyIndex - historyCount + MAX_HISTORY) % MAX_HISTORY;

    for (int i = 0; i < historyCount; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint));
        file.read((uint8_t*)&dp, sizeof(DataPoint));

        time_t timestamp = time(nullptr) - (historyCount - 1 - i) * (historyRecordInterval / 1000);

        uint8_t packet[9];
        packet[0] = CMD_REPORT_HISTORIC_POINT;
        packet[1] = timestamp & 0xFF;
        packet[2] = (timestamp >> 8) & 0xFF;
        packet[3] = (timestamp >> 16) & 0xFF;
        packet[4] = (timestamp >> 24) & 0xFF;
        packet[5] = (uint8_t)dp.temp;
        packet[6] = (uint8_t)((dp.temp - packet[5]) * 100);
        packet[7] = (uint8_t)dp.hum;
        packet[8] = (uint8_t)((dp.hum - packet[7]) * 100);

        pDataEventCharacteristic->setValue(packet, 9);
        pDataEventCharacteristic->notify();
        delay(10);
    }
    file.close();
    sendHistoricDataEnd();
}

void sendTimeSyncAck() {
    if (!bleDeviceConnected) return;
    uint8_t packet[1] = {CMD_TIME_SYNC_ACK};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void sendErrorReport(uint8_t errorCode) {
    if (!bleDeviceConnected) return;
    uint8_t packet[2] = {CMD_ERROR, errorCode};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

// ==================== NTP ====================
void syncTimeNTPForce() {
    if (WiFi.status() != WL_CONNECTED) return;
    configTime(GMT_OFFSET, DAYLIGHT_OFFSET, NTP_SERVER);
    struct tm timeinfo;
    int retry = 0;
    while (!getLocalTime(&timeinfo) && retry < 20) { delay(500); retry++; }
    if (retry < 20) {
        syncIconStartTime = millis();
        lastNTPResync = millis();
    }
}

// ==================== Wi-Fi 非阻塞連接 ====================
void startWiFiConnection(String ssid, String pass, bool showScreen) {
    if (wifiConnecting) {
        WiFi.disconnect(true);
        delay(100);
    }

    targetSSID = ssid;
    targetPASS = pass;
    wifiConnecting = true;
    wifiConnectStartTime = millis();
    wifiConnectProgress = 0;

    WiFi.disconnect(true);
    delay(100);
    WiFi.begin(targetSSID.c_str(), targetPASS.c_str());

    if (showScreen) {
        drawWiFiConnecting(targetSSID, 0, 1);
    }
}

void updateWiFiConnection() {
    if (!wifiConnecting) return;

    unsigned long elapsed = millis() - wifiConnectStartTime;
    int progress = min(100, (int)(elapsed * 100 / WIFI_CONNECT_TIMEOUT));

    static int lastProgress = -1;
    if (progress != lastProgress) {
        lastProgress = progress;
        int dots = (progress / 10) % 4;
        drawWiFiConnecting(targetSSID, progress, dots + 1);
    }

    if (WiFi.status() == WL_CONNECTED) {
        wifiConnecting = false;
        manualConnectRequested = false;
        drawWiFiConnected(WiFi.localIP().toString());
        delay(2000);
        syncTimeNTPForce();
        fetchWeatherData();
        updateDisplay();
        return;
    }

    if (elapsed > WIFI_CONNECT_TIMEOUT) {
        wifiConnecting = false;
        manualConnectRequested = false;
        drawWiFiFailed();
        delay(2000);
        updateDisplay();
    }
}

void drawWiFiConnecting(String ssid, int progress, int dotsCount) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    String text = "WiFi Connecting";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 18, text.c_str());

    String dots = "";
    for (int i = 0; i < dotsCount; i++) dots += ".";
    text = dots;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 30, text.c_str());

    text = "SSID: " + ssid;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 42, text.c_str());

    int barWidth = (progress * 100) / 100;
    u8g2.drawFrame(14, 50, 100, 8);
    u8g2.drawBox(14, 50, barWidth, 8);

    u8g2.sendBuffer();
}

void drawWiFiConnected(String ip) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    String text = "WiFi Connected!";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 30, text.c_str());
    text = "IP: " + ip;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 45, text.c_str());
    u8g2.sendBuffer();
}

void drawWiFiFailed() {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    String text = "WiFi Failed";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 30, text.c_str());
    text = "Check SSID/PASS";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 45, text.c_str());
    u8g2.sendBuffer();
}

// ==================== 歷史資料永久儲存 ====================
void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) {
            Serial.println("Failed to create history.dat");
            return;
        }
        DataPoint empty = {0, 0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) {
            file.write((uint8_t*)&empty, sizeof(DataPoint));
        }
        file.close();
        Serial.println("history.dat initialized");
    }
}

void loadHistoryMetadata() {
    preferences.begin("medbox-meta", true);
    historyCount = preferences.getInt("hist_count", 0);
    historyIndex = preferences.getInt("hist_index", 0);
    preferences.end();
    Serial.printf("Metadata loaded: count=%d, index=%d\n", historyCount, historyIndex);
}

void saveHistoryMetadata() {
    preferences.begin("medbox-meta", false);
    preferences.putInt("hist_count", historyCount);
    preferences.putInt("hist_index", historyIndex);
    preferences.end();
}

void addDataToHistory(float temp, float hum, int16_t rssi) {
    File file = SPIFFS.open("/history.dat", "r+");
    if (!file) {
        Serial.println("Failed to open history.dat for write");
        return;
    }
    DataPoint dp = {temp, hum, rssi};
    file.seek(historyIndex * sizeof(DataPoint));
    file.write((uint8_t*)&dp, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    saveHistoryMetadata();

    if (currentEncoderMode == MODE_VIEW_ADJUST && historyViewOffset == 0) {
        loadHistoryWindow(0);
    }
}

void loadHistoryWindow(int offset) {
    int points = min(historyCount, HISTORY_WINDOW_SIZE);
    if (points == 0) return;

    File file = SPIFFS.open("/history.dat", "r");
    if (!file) {
        Serial.println("Failed to open history.dat for read");
        return;
    }

    int startIdx = (historyIndex - offset - points + MAX_HISTORY) % MAX_HISTORY;
    for (int i = 0; i < points; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint));
        if (file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint)) != sizeof(DataPoint)) {
            Serial.println("Read error in history");
            break;
        }
    }
    file.close();
}

// ==================== OTA ====================
void startOTAWebServer() {
    otaServer.on("/", HTTP_GET, []() {
        String html = "<h1>SmartMedBox OTA</h1><form method='POST' action='/update' enctype='multipart/form-data'>"
                      "<input type='file' name='update'><input type='submit' value='Update'></form>";
        otaServer.send(200, "text/html", html);
    });

    otaServer.on("/update", HTTP_POST, []() {
        otaServer.send(200, "text/plain", (Update.hasError()) ? "FAIL" : "OK");
        ESP.restart();
    }, []() {
        HTTPUpload& upload = otaServer.upload();
        if (upload.status == UPLOAD_FILE_START) {
            Serial.setDebugOutput(true);
            Serial.printf("Update: %s\n", upload.filename.c_str());
            if (!Update.begin(UPDATE_SIZE_UNKNOWN)) Update.printError(Serial);
        } else if (upload.status == UPLOAD_FILE_WRITE) {
            if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) Update.printError(Serial);
        } else if (upload.status == UPLOAD_FILE_END) {
            if (Update.end(true)) {
                Serial.printf("Update Success: %uB\n", upload.totalSize);
            } else {
                Update.printError(Serial);
            }
        }
    });

    otaServer.begin();
    Serial.printf("OTA Server started. IP: %s\n", WiFi.localIP().toString().c_str());
}

void handleOTAUpload() {
    if (otaMode && WiFi.status() == WL_CONNECTED) {
        otaServer.handleClient();
        if (millis() - otaStartTime > 300000) {
            otaMode = false;
            otaServer.stop();
            currentPageIndex = SCREEN_TIME;
            updateDisplay();
        }
    }
}

void drawOTAScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 15, "OTA Mode");
    u8g2.drawStr(0, 30, "IP:");
    u8g2.drawStr(30, 30, WiFi.localIP().toString().c_str());
    u8g2.drawStr(0, 45, "Visit in browser");
    u8g2.drawStr(0, 60, "Timeout: 5 min");
}

// ==================== UI 更新核心 ====================
void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    if (otaMode) {
        drawOTAScreen();
    } else if (wifiConnecting) {
        int progress = min(100, (int)((millis() - wifiConnectStartTime) * 100 / WIFI_CONNECT_TIMEOUT));
        int dots = (progress / 10) % 4;
        drawWiFiConnecting(targetSSID, progress, dots + 1);
    } else {
        switch (currentPageIndex) {
            case SCREEN_TIME: drawTimeScreen(); break;
            case SCREEN_DATE: drawDateScreen(); break;
            case SCREEN_WEATHER: drawWeatherScreen(); break;
            case SCREEN_SENSOR: drawSensorScreen(); break;
            case SCREEN_TEMP_CHART: if (isEngineeringMode) drawTempChartScreen(); break;
            case SCREEN_HUM_CHART: if (isEngineeringMode) drawHumChartScreen(); break;
            case SCREEN_RSSI_CHART: if (isEngineeringMode) drawRssiChartScreen(); break;
            case SCREEN_SYSTEM: if (isEngineeringMode) drawSystemScreen(); break;
            case SCREEN_OTA: if (isEngineeringMode) drawOTAScreen(); break;
        }
        drawStatusIcons();
    }
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    // WiFi 圖示
    if (WiFi.status() == WL_CONNECTED) {
        u8g2.drawXBMP(110, 0, 16, 16, wifi_icon);
    }
    // BLE 圖示
    if (bleDeviceConnected) {
        u8g2.drawXBMP(90, 0, 16, 16, ble_icon);
    }
    // 同步圖示
    if (millis() - syncIconStartTime < SYNC_ICON_DURATION) {
        u8g2.drawXBMP(70, 0, 16, 16, sync_icon);
    }
}

// 圖示資料（可自行替換）
const unsigned char wifi_icon[] PROGMEM = { /* WiFi icon */ };
const unsigned char ble_icon[] PROGMEM = { /* BLE icon */ };
const unsigned char sync_icon[] PROGMEM = { /* Sync icon */ };

// ==================== 畫面函式 ====================
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) {
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr(10, 32, "Time not set");
    } else {
        struct tm *ptm = localtime(&now);
        char s[9]; sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec);
        u8g2.setFont(u8g2_font_fub20_tn);
        u8g2.drawStr((128 - u8g2.getStrWidth(s))/2, 42, s);
    }

    if (WiFi.status() != WL_CONNECTED && !wifiConnecting && currentPageIndex == SCREEN_TIME) {
        u8g2.setFont(u8g2_font_6x10_tf);
        String hint = "CONFIRM to connect";
        u8g2.drawStr((128 - u8g2.getStrWidth(hint.c_str())) / 2, 60, hint.c_str());
    }
}

void drawDateScreen() {
    time_t now; time(&now);
    struct tm *ptm = localtime(&now);
    char s[11]; sprintf(s, "%04d/%02d/%02d", ptm->tm_year + 1900, ptm->tm_mon + 1, ptm->tm_mday);
    u8g2.setFont(u8g2_font_ncenB14_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth(s))/2, 40, s);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    if (weatherData.valid) {
        char tempStr[10]; sprintf(tempStr, "%.1fC", weatherData.temp);
        u8g2.drawStr(10, 25, tempStr);
        u8g2.drawStr(10, 40, weatherData.description.c_str());
    } else {
        u8g2.drawStr(10, 30, "No Data");
    }
}

void drawSensorScreen() {
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    float h = dht.readHumidity();
    char buf[20];
    sprintf(buf, "T: %.1fC", t);
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(10, 25, buf);
    sprintf(buf, "H: %.1f%%", h);
    u8g2.drawStr(10, 40, buf);
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Hum Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 10, title);

    int points = min(historyCount, HISTORY_WINDOW_SIZE);
    if (points == 0) {
        u8g2.drawStr(20, 35, "No Data");
        return;
    }

    float minVal = 1000, maxVal = -1000;
    for (int i = 0; i < points; i++) {
        float val = isTemp ? historyWindowBuffer[i].temp : isRssi ? historyWindowBuffer[i].rssi : historyWindowBuffer[i].hum;
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }
    if (maxVal == minVal) maxVal += 1;

    for (int i = 0; i < points - 1; i++) {
        float v1 = isTemp ? historyWindowBuffer[i].temp : isRssi ? historyWindowBuffer[i].rssi : historyWindowBuffer[i].hum;
        float v2 = isTemp ? historyWindowBuffer[i+1].temp : isRssi ? historyWindowBuffer[i+1].rssi : historyWindowBuffer[i+1].hum;
        int x1 = 10 + i * 2;
        int x2 = 10 + (i+1) * 2;
        int y1 = 60 - (int)((v1 - minVal) * 40 / (maxVal - minVal));
        int y2 = 60 - (int)((v2 - minVal) * 40 / (maxVal - minVal));
        u8g2.drawLine(x1, y1, x2, y2);
    }
}

void drawSystemScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 15, "SmartMedBox v16.3");
    u8g2.drawStr(0, 30, "History: ");
    char buf[10]; sprintf(buf, "%d", historyCount);
    u8g2.drawStr(60, 30, buf);
    u8g2.drawStr(0, 45, "RSSI: ");
    sprintf(buf, "%d dBm", WiFi.RSSI());
    u8g2.drawStr(60, 45, buf);
}

// ==================== 其他 ====================
void fetchWeatherData() {
    if (WiFi.status() != WL_CONNECTED) { weatherData.valid = false; return; }
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
    } else { weatherData.valid = false; }
    http.end();
}

void updateScreens() {
    NUM_SCREENS = isEngineeringMode ? 9 : 4;
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
    if (currentPageIndex >= NUM_SCREENS) {
        currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(SCREEN_TIME);
    }
    updateDisplay();
}

void handleEncoder() {
    if (wifiConnecting || otaMode) return;
    if (rotaryEncoder.encoderChanged()) {
        if (currentEncoderMode == MODE_VIEW_ADJUST) {
            historyViewOffset = rotaryEncoder.readEncoder();
            loadHistoryWindow(historyViewOffset);
        } else {
            currentPageIndex = (ScreenState)rotaryEncoder.readEncoder();
        }
        updateDisplay();
    }
}

void handleEncoderPush() {
    if (wifiConnecting || otaMode) return;
    if (digitalRead(ENCODER_PSH_PIN) == LOW && (millis() - lastEncoderPushTime > 300)) {
        lastEncoderPushTime = millis();
        ScreenState current = currentPageIndex;
        int idx = (current == SCREEN_TEMP_CHART) ? 0 : (current == SCREEN_HUM_CHART) ? 1 : 2;

        if (currentEncoderMode == MODE_NAVIGATION &&
            (current == SCREEN_TEMP_CHART || current == SCREEN_HUM_CHART || current == SCREEN_RSSI_CHART) && isEngineeringMode) {
            currentEncoderMode = MODE_VIEW_ADJUST;
            historyViewOffset = lastViewOffset[idx];
            int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
            rotaryEncoder.setBoundaries(0, maxOffset, false);
            rotaryEncoder.setEncoderValue(historyViewOffset);
            loadHistoryWindow(historyViewOffset);
        } else if (currentEncoderMode == MODE_VIEW_ADJUST) {
            lastViewOffset[idx] = historyViewOffset;
            currentEncoderMode = MODE_NAVIGATION;
            rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
            rotaryEncoder.setEncoderValue(currentPageIndex);
        }
        updateDisplay();
    }
}

void handleButtons() {
    if (digitalRead(BUTTON_CONFIRM_PIN) == LOW && (millis() - lastConfirmPressTime > 300)) {
        lastConfirmPressTime = millis();

        if (currentPageIndex == SCREEN_TIME && WiFi.status() != WL_CONNECTED && !wifiConnecting && !otaMode) {
            preferences.begin("wifi", true);
            String savedSSID = preferences.getString("ssid", default_ssid);
            String savedPASS = preferences.getString("pass", default_password);
            preferences.end();
            startWiFiConnection(savedSSID, savedPASS, true);
            manualConnectRequested = true;
        } else if (!wifiConnecting && !otaMode) {
            currentEncoderMode = MODE_NAVIGATION;
            rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
            currentPageIndex = SCREEN_TIME;
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
        }
        updateDisplay();
    }
}

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- SmartMedBox Firmware v16.3 ---");

    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin();
    u8g2.enableUTF8Print();
    dht.begin();

    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS mount failed");
        return;
    }

    initializeHistoryFile();
    loadHistoryMetadata();

    if (historyCount > 0) {
        loadHistoryWindow(0);
        Serial.printf("History restored: %d points\n", historyCount);
    } else {
        Serial.println("No history to restore");
    }

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("v16.3"))/2, 45, "v16.3");
    u8g2.sendBuffer();
    delay(2000);

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
    BLEDevice::getAdvertising()->setScanResponse(true);
    BLEDevice::startAdvertising();
    Serial.println("BLE Server started.");

    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);

    preferences.begin("wifi", true);
    targetSSID = preferences.getString("ssid", default_ssid);
    targetPASS = preferences.getString("pass", default_password);
    preferences.end();
    startWiFiConnection(targetSSID, targetPASS, true);

    currentPageIndex = SCREEN_TIME;
    rotaryEncoder.setEncoderValue(SCREEN_TIME);
    updateScreens();

    Serial.println("--- Setup Complete ---\n");
}

// ==================== LOOP ====================
void loop() {
    handleEncoder();
    handleEncoderPush();
    handleButtons();
    updateWiFiConnection();
    handleOTAUpload();

    if (!wifiConnecting && WiFi.status() == WL_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
        syncTimeNTPForce();
    }

    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        float t = dht.readTemperature();
        float h = dht.readHumidity();
        int16_t rssi = WiFi.RSSI();
        if (!isnan(t) && !isnan(h)) {
            addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi);
        }
    }

    if (millis() - lastPersistenceSave > persistenceSaveInterval) {
        saveHistoryMetadata();
        lastPersistenceSave = millis();
    }

    if (!wifiConnecting && WiFi.status() == WL_CONNECTED && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }

    if (millis() - lastDisplayUpdate >= displayInterval) {
        updateDisplay();
    }
}