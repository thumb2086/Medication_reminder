/*
  SmartMedBox Firmware v16.4 (最終完美版)
  修復：網路連接失敗、BLE 回呼錯誤、編譯錯誤
  功能：Wi-Fi 進度條、NTP 多伺服器、自動重連、圖示正確
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
const char* ntpServers[] = {"time.google.com", "pool.ntp.org", "time.nist.gov"};
const int numNTPServers = 3;
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;

// ==================== BLE ====================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define COMMAND_CHANNEL_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define DATA_EVENT_CHANNEL_UUID "c8c7c599-809c-43a5-b825-1038aa349e5d"

#define CMD_TIME_SYNC               0x11
#define CMD_WIFI_CREDENTIALS        0x12
#define CMD_SET_ENGINEERING_MODE    0x13
#define CMD_REQUEST_STATUS          0x20
#define CMD_REQUEST_ENV             0x30
#define CMD_REQUEST_HISTORIC        0x31
#define CMD_REPORT_STATUS           0x80
#define CMD_REPORT_TAKEN            0x81
#define CMD_TIME_SYNC_ACK           0x82
#define CMD_REPORT_ENV              0x90
#define CMD_REPORT_HISTORIC_POINT   0x91
#define CMD_REPORT_HISTORIC_END     0x92
#define CMD_ERROR                   0xEE

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE, I2C_SCL_PIN, I2C_SDA_PIN);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;

// ==================== 狀態 ====================
enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
const int NUM_SCREENS = 8;

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
bool syncInProgress = false;
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
unsigned long lastWiFiReconnect = 0;
const unsigned long WIFI_RECONNECT_INTERVAL = 30000;

// ==================== 函式宣告 ====================
void updateDisplay();
void drawStatusIcons();
bool isWiFiReallyConnected();
bool connectWiFiWithProgress();
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
void fetchWeatherData();
void handleEncoder();
void handleEncoderPush();
void handleButtons();
const char* getWeatherIcon(const String &desc);
void sendBoxStatus();
void sendHistoricData();
void sendHistoricDataEnd();

// ==================== Wi-Fi 狀態校正 ====================
bool isWiFiReallyConnected() {
    return (WiFi.status() == WL_CONNECTED) &&
           (WiFi.localIP() != IPAddress(0,0,0,0)) &&
           (WiFi.RSSI() != 0);
}

// ==================== Wi-Fi 連線 + 進度顯示 ====================
bool connectWiFiWithProgress() {
    preferences.begin("wifi", true);
    String ssid = preferences.getString("ssid", default_ssid);
    String pass = preferences.getString("pass", default_password);
    preferences.end();

    Serial.printf("Connecting to WiFi: %s\n", ssid.c_str());

    WiFi.mode(WIFI_STA);
    WiFi.persistent(false);
    WiFi.begin(ssid.c_str(), pass.c_str());

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(10, 20, "WiFi Connecting...");
    u8g2.drawStr(10, 35, ssid.c_str());
    u8g2.sendBuffer();

    int dots = 0;
    unsigned long startTime = millis();
    while (!isWiFiReallyConnected() && millis() - startTime < 20000) {
        delay(500);
        dots = (dots + 1) % 4;
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Connecting");
        char dotStr[5] = "...";
        dotStr[dots] = '\0';
        u8g2.drawStr(85, 20, dotStr);
        u8g2.drawStr(10, 35, ssid.c_str());
        u8g2.sendBuffer();
    }

    if (isWiFiReallyConnected()) {
        Serial.printf("WiFi Connected! IP: %s\n", WiFi.localIP().toString().c_str());
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Connected!");
        u8g2.drawStr(10, 35, WiFi.localIP().toString().c_str());
        u8g2.sendBuffer();
        delay(1500);
        return true;
    } else {
        Serial.println("WiFi Connection Failed");
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Failed");
        u8g2.drawStr(10, 35, "Check SSID/PASS");
        u8g2.sendBuffer();
        delay(2000);
        return false;
    }
}

// ==================== NTP 強制同步 ====================
void syncTimeNTPForce() {
    if (!isWiFiReallyConnected()) return;

    Serial.println("NTP Sync Start...");
    bool synced = false;

    for (int i = 0; i < numNTPServers && !synced; i++) {
        Serial.printf("Trying NTP: %s\n", ntpServers[i]);
        configTime(GMT_OFFSET, DAYLIGHT_OFFSET, ntpServers[i]);

        struct tm timeinfo;
        int retry = 0;
        while (!getLocalTime(&timeinfo) && retry < 25) {
            delay(200);
            retry++;
        }

        if (retry < 25) {
            Serial.printf("NTP Synced: %s\n", ntpServers[i]);
            syncIconStartTime = millis();
            syncInProgress = true;
            lastNTPResync = millis();
            synced = true;
        }
    }

    if (!synced) {
        Serial.println("All NTP Failed");
    }
}

// ==================== 歷史記錄 ====================
void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) return;
        DataPoint emptyPoint = {0.0, 0.0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) {
            file.write((uint8_t*)&emptyPoint, sizeof(DataPoint));
        }
        file.close();
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
    File file = SPIFFS.open("/history.dat", "r+");
    if (!file) return;

    DataPoint newDataPoint = {temp, hum, rssi};
    long pos = historyIndex * sizeof(DataPoint);
    file.seek(pos);
    file.write((uint8_t*)&newDataPoint, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    saveHistoryMetadata();
}

void loadHistoryWindow(int offset) {
    int pointsToRead = min(historyCount, HISTORY_WINDOW_SIZE);
    if (pointsToRead == 0) return;

    File file = SPIFFS.open("/history.dat", "r");
    if (!file) return;

    int startIdx = (historyIndex - offset - pointsToRead + MAX_HISTORY * 2) % MAX_HISTORY;

    for (int i = 0; i < pointsToRead; i++) {
        int currentIdx = (startIdx + i) % MAX_HISTORY;
        long pos = currentIdx * sizeof(DataPoint);
        file.seek(pos);
        file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
}

// ==================== BLE 回呼 ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleDeviceConnected = true;
        Serial.println("BLE Connected");
        delay(500);
        pServer->getAdvertising()->start();
    }
    void onDisconnect(BLEServer* pServer) {
        bleDeviceConnected = false;
        Serial.println("BLE Disconnected");
        delay(500);
        pServer->getAdvertising()->start();
    }
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
                syncInProgress = true;
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
                    WiFi.disconnect(true);
                    delay(1000);
                    connectWiFiWithProgress();
                    sendTimeSyncAck();
                }
            }
            break;

        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                // engineeringMode = (data[1] == 0x01);
                sendTimeSyncAck();
            }
            break;

        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC: sendHistoricData(); break;
        default: sendErrorReport(0x03); break;
    }
}

void sendTimeSyncAck() {
    if (!bleDeviceConnected) return;
    uint8_t ack[1] = {CMD_TIME_SYNC_ACK};
    pDataEventCharacteristic->setValue(ack, 1);
    pDataEventCharacteristic->notify();
}

void sendErrorReport(uint8_t errorCode) {
    if (!bleDeviceConnected) return;
    uint8_t err[2] = {CMD_ERROR, errorCode};
    pDataEventCharacteristic->setValue(err, 2);
    pDataEventCharacteristic->notify();
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    uint8_t status[2] = {CMD_REPORT_STATUS, 0x00};
    pDataEventCharacteristic->setValue(status, 2);
    pDataEventCharacteristic->notify();
}

void sendSensorDataReport() {
    if (!bleDeviceConnected) return;
    float temp = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    float hum = dht.readHumidity();
    if (isnan(temp) || isnan(hum)) return;

    uint8_t report[5] = {CMD_REPORT_ENV};
    report[1] = (int)temp;
    report[2] = (int)((temp - (int)temp) * 100);
    report[3] = (int)hum;
    report[4] = (int)((hum - (int)hum) * 100);
    pDataEventCharacteristic->setValue(report, 5);
    pDataEventCharacteristic->notify();
}

void sendHistoricData() {
    if (!bleDeviceConnected || historyCount == 0) {
        uint8_t end[1] = {CMD_REPORT_HISTORIC_END};
        pDataEventCharacteristic->setValue(end, 1);
        pDataEventCharacteristic->notify();
        return;
    }
    uint8_t end[1] = {CMD_REPORT_HISTORIC_END};
    pDataEventCharacteristic->setValue(end, 1);
    pDataEventCharacteristic->notify();
}

// ==================== UI 核心 ====================
void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    switch (currentPageIndex) {
        case SCREEN_TIME: drawTimeScreen(); break;
        case SCREEN_DATE: drawDateScreen(); break;
        case SCREEN_WEATHER: drawWeatherScreen(); break;
        case SCREEN_SENSOR: drawSensorScreen(); break;
        case SCREEN_TEMP_CHART: drawTempChartScreen(); break;
        case SCREEN_HUM_CHART: drawHumChartScreen(); break;
        case SCREEN_RSSI_CHART: drawRssiChartScreen(); break;
        case SCREEN_SYSTEM: drawSystemScreen(); break;
    }

    drawStatusIcons();
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentPageIndex != SCREEN_TIME) return;

    u8g2.setFont(u8g2_font_open_iconic_all_1x_t);
    int x = 0;
    const int spacing = 12;

    if (bleDeviceConnected) {
        u8g2.drawStr(x, 10, "d");
        x += spacing;
    }

    if (syncInProgress && (millis() - syncIconStartTime < SYNC_ICON_DURATION) && (millis() / 500) % 2 == 0) {
        u8g2.drawStr(x, 10, "Q");
        x += spacing;
    }

    if (syncInProgress && millis() - syncIconStartTime >= SYNC_ICON_DURATION) {
        syncInProgress = false;
    }

    u8g2.drawStr(118, 10, isWiFiReallyConnected() ? "W" : "!");
}

// ==================== 圖表 ====================
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);

    if (historyCount < 2) {
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(10, 35, "No Data");
        return;
    }

    loadHistoryWindow(historyViewOffset);

    int displayCount = min((int)HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) {
        u8g2.drawStr(10, 35, "Insufficient Data");
        return;
    }

    float minVal = isRssi ? 0 : 101;
    float maxVal = isRssi ? -100 : -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? (float)historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }

    float range = maxVal - minVal;
    if (isRssi) {
        minVal = max(minVal, -90.0f);
        maxVal = min(maxVal, -30.0f);
        range = maxVal - minVal;
        if (range < 10.0f) range = 10.0f;
    } else if (range < 1.0f) {
        minVal -= 0.5f; maxVal += 0.5f;
        range = maxVal - minVal;
    }
    if (range < 0.1f) range = 1.0f;

    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr);
    char buf[10];
    sprintf(buf, isRssi ? "%d" : (isTemp ? "%.1f" : "%.0f"), maxVal);
    u8g2.drawStr(0, chartY + 5, buf);
    sprintf(buf, isRssi ? "%d" : (isTemp ? "%.1f" : "%.0f"), minVal);
    u8g2.drawStr(0, chartY + chartH, buf);

    u8g2.drawFrame(chartX, chartY, chartW, chartH);

    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? (float)historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        int x = chartX + (i * chartW / displayCount);
        int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; lastY = y;
    }

    char countStr[20];
    sprintf(countStr, "[%d/%d]", displayCount, historyCount);
    u8g2.drawStr(128 - u8g2.getStrWidth(countStr) - 2, 10, countStr);

    char offsetStr[10];
    if (historyViewOffset == 0) strcpy(offsetStr, "Now");
    else {
        float hours = (historyViewOffset * historyRecordInterval) / 3600000.0;
        sprintf(offsetStr, "-%.1fh", hours);
    }
    u8g2.drawStr(chartX + 2, chartY + chartH - 2, offsetStr);

    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        u8g2.drawStr(100, 64, "VIEW");
    }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart (dBm)", false, true); }

// ==================== 其他畫面 ====================
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) {
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr(10, 32, "Time not set");
    } else {
        struct tm * ptm = localtime(&now);
        u8g2.setFont(u8g2_font_fub20_tn);
        char timeStr[9];
        sprintf(timeStr, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec);
        u8g2.drawStr((128 - u8g2.getStrWidth(timeStr)) / 2, 42, timeStr);
    }
}

void drawDateScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); return; }
    struct tm * ptm = localtime(&now);
    const char* weekAbbr[] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
    const char* monthAbbr[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

    char dayStr[4], yearStr[6];
    sprintf(dayStr, "%02d", ptm->tm_mday);
    sprintf(yearStr, "%d", ptm->tm_year + 1900);

    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, dayStr);
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, weekAbbr[ptm->tm_wday]);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(72, 40, monthAbbr[ptm->tm_mon]);
    u8g2.drawStr(72, 56, yearStr);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr(0, 12, city.c_str());
    if (!isWiFiReallyConnected()) {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return;
    }
    if (weatherData.valid) {
        char buffer[20];
        const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); u8g2.drawStr(5, 50, icon);

        u8g2.setFont(u8g2_font_fub25_tn);
        sprintf(buffer, "%.1f", weatherData.temp);
        u8g2.drawStr(45, 32, buffer);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buffer) + 4, 15, 3);

        u8g2.setFont(u8g2_font_unifont_t_chinese2); u8g2.drawStr(45, 50, weatherData.description.c_str());

        u8g2.setFont(u8g2_font_ncenR10_tr); sprintf(buffer, "H:%d%%", weatherData.humidity); u8g2.drawStr(45, 64, buffer);
    } else {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "No data");
    }
}

void drawSensorScreen() {
    float h = dht.readHumidity();
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    if (isnan(h) || isnan(t)) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 40, "Sensor Error!"); return; }
    char buffer[20];
    u8g2.setFont(u8g2_font_helvB10_tr); u8g2.drawStr((128 - u8g2.getStrWidth("INDOOR")) / 2, 12, "INDOOR");
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 38, "TEMP");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buffer, "%.1f C", t); u8g2.drawStr(128 - u8g2.getStrWidth(buffer) - 10, 38, buffer);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 62, "HUMI");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buffer, "%.0f %%", h); u8g2.drawStr(128 - u8g2.getStrWidth(buffer) - 10, 62, buffer);
}

void drawSystemScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 12, "System Info");
    u8g2.setFont(u8g2_font_profont11_tf);

    int y = 28;

    String ssid = WiFi.SSID(); if (ssid == "") ssid = "N/A";
    if (isWiFiReallyConnected()) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y+=12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y+=12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y+=12;
    } else {
        u8g2.drawStr(0, y, "WiFi Disconnected"); y+=12;
    }

    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap() / 1024) + " KB").c_str()); y+=12;
    u8g2.drawStr(0, y, ("Up: " + String(millis()/60000) + " min").c_str());
}

const char* getWeatherIcon(const String &desc) {
    String s = desc; s.toLowerCase();
    if (s.indexOf("clear") >= 0 || s.indexOf("晴") >= 0) return "A";
    if (s.indexOf("cloud") >= 0 || s.indexOf("雲") >= 0 || s.indexOf("阴") >= 0) return "C";
    if (s.indexOf("rain") >= 0 || s.indexOf("雨") >= 0) return "R";
    if (s.indexOf("snow") >= 0 || s.indexOf("雪") >= 0) return "S";
    if (s.indexOf("thunder") >= 0 || s.indexOf("雷") >= 0) return "T";
    if (s.indexOf("fog") >= 0 || s.indexOf("霧") >= 0 || s.indexOf("霾") >= 0) return "M";
    return "C";
}

void fetchWeatherData() {
    if (!isWiFiReallyConnected()) { weatherData.valid = false; return; }
    HTTPClient http;
    String serverPath = "http://api.openweathermap.org/data/2.5/weather?q=" + city + "," +
                        countryCode + "&units=metric&lang=zh_tw&APPID=" + openWeatherMapApiKey;
    http.begin(serverPath.c_str());
    int httpResponseCode = http.GET();

    if (httpResponseCode == HTTP_CODE_OK) {
        String payload = http.getString();
        int descStart = payload.indexOf("\"description\":\"") + 15;
        int descEnd = payload.indexOf("\"", descStart);
        if(descStart > 14 && descEnd > descStart) weatherData.description = payload.substring(descStart, descEnd);
        int tempStart = payload.indexOf("\"temp\":") + 7;
        int tempEnd = payload.indexOf(",", tempStart);
        if(tempStart > 6 && tempEnd > tempStart) weatherData.temp = payload.substring(tempStart, tempEnd).toFloat();
        int humStart = payload.indexOf("\"humidity\":") + 11;
        int humEnd = payload.indexOf("}", humStart);
        if(humStart > 10 && humEnd > humStart) weatherData.humidity = payload.substring(humStart, humEnd).toInt();
        weatherData.valid = true;
    } else {
        weatherData.valid = false;
    }
    http.end();
}

// ==================== 旋鈕與按鈕 ====================
void handleEncoder() {
    if (rotaryEncoder.encoderChanged()) {
        if (currentEncoderMode == MODE_VIEW_ADJUST) {
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

        ScreenState currentScreen = currentPageIndex;

        if(currentScreen == SCREEN_TEMP_CHART || currentScreen == SCREEN_HUM_CHART || currentScreen == SCREEN_RSSI_CHART){
            int idx = (currentScreen == SCREEN_TEMP_CHART) ? 0 : (currentScreen == SCREEN_HUM_CHART) ? 1 : 2;

            if (currentEncoderMode == MODE_NAVIGATION) {
                currentEncoderMode = MODE_VIEW_ADJUST;
                historyViewOffset = lastViewOffset[idx];
                int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
                rotaryEncoder.setBoundaries(0, maxOffset, false);
                rotaryEncoder.setEncoderValue(historyViewOffset);
            } else {
                lastViewOffset[idx] = historyViewOffset;
                currentEncoderMode = MODE_NAVIGATION;
                rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
                rotaryEncoder.setEncoderValue((long)currentScreen);
            }
        }
        updateDisplay();
    }
}

void handleButtons() {
    const unsigned long DEBOUNCE_DELAY = 300;
    if (digitalRead(BUTTON_CONFIRM_PIN) == LOW) {
        if (millis() - lastConfirmPressTime > DEBOUNCE_DELAY) {
            lastConfirmPressTime = millis();

            if (currentEncoderMode != MODE_NAVIGATION) {
                ScreenState currentScreen = currentPageIndex;
                if(currentScreen == SCREEN_TEMP_CHART || currentScreen == SCREEN_HUM_CHART || currentScreen == SCREEN_RSSI_CHART){
                    int idx = (currentScreen == SCREEN_TEMP_CHART) ? 0 : (currentScreen == SCREEN_HUM_CHART) ? 1 : 2;
                    lastViewOffset[idx] = historyViewOffset;
                }
                currentEncoderMode = MODE_NAVIGATION;
                rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
            }
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
            currentPageIndex = SCREEN_TIME;
            updateDisplay();
        }
    }
}

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n=== SmartMedBox v16.4 ===");

    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    delay(100);
    u8g2.begin();
    u8g2.enableUTF8Print();
    dht.begin();

    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS Failed");
        return;
    }
    initializeHistoryFile();

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
    loadHistoryMetadata();

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr(20, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(50, 45, "v16.4");
    u8g2.sendBuffer();
    delay(1500);

    BLEDevice::init("SmartMedBox");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic *pCommandChar = pService->createCharacteristic(COMMAND_CHANNEL_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommandChar->setCallbacks(new CommandCallbacks());
    pDataEventCharacteristic = pService->createCharacteristic(DATA_EVENT_CHANNEL_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pDataEventCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
    Serial.println("BLE OK");

    if (connectWiFiWithProgress()) {
        syncTimeNTPForce();
        fetchWeatherData();
    }

    currentPageIndex = SCREEN_TIME;
    rotaryEncoder.setEncoderValue(SCREEN_TIME);
}

// ==================== LOOP ====================
void loop() {
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (!isWiFiReallyConnected() && millis() - lastWiFiReconnect > WIFI_RECONNECT_INTERVAL) {
        lastWiFiReconnect = millis();
        connectWiFiWithProgress();
    }

    if (millis() - lastDisplayUpdate > displayInterval) {
        updateDisplay();
    }

    if (millis() - lastHistoryRecord > historyRecordInterval) {
        float temp = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
        float hum = dht.readHumidity();
        int16_t rssi = WiFi.RSSI();
        if (!isnan(temp) && !isnan(hum)) {
            addDataToHistory(temp, hum, rssi);
        }
        lastHistoryRecord = millis();
    }

    if (isWiFiReallyConnected() && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
        syncTimeNTPForce();
    }

    if (isWiFiReallyConnected() && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }
}/*
  SmartMedBox Firmware v16.4 (最終完美版)
  修復：網路連接失敗、BLE 回呼錯誤、編譯錯誤
  功能：Wi-Fi 進度條、NTP 多伺服器、自動重連、圖示正確
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
const char* ntpServers[] = {"time.google.com", "pool.ntp.org", "time.nist.gov"};
const int numNTPServers = 3;
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;

// ==================== BLE ====================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define COMMAND_CHANNEL_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define DATA_EVENT_CHANNEL_UUID "c8c7c599-809c-43a5-b825-1038aa349e5d"

#define CMD_TIME_SYNC               0x11
#define CMD_WIFI_CREDENTIALS        0x12
#define CMD_SET_ENGINEERING_MODE    0x13
#define CMD_REQUEST_STATUS          0x20
#define CMD_REQUEST_ENV             0x30
#define CMD_REQUEST_HISTORIC        0x31
#define CMD_REPORT_STATUS           0x80
#define CMD_REPORT_TAKEN            0x81
#define CMD_TIME_SYNC_ACK           0x82
#define CMD_REPORT_ENV              0x90
#define CMD_REPORT_HISTORIC_POINT   0x91
#define CMD_REPORT_HISTORIC_END     0x92
#define CMD_ERROR                   0xEE

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE, I2C_SCL_PIN, I2C_SDA_PIN);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;

// ==================== 狀態 ====================
enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
const int NUM_SCREENS = 8;

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
bool syncInProgress = false;
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
unsigned long lastWiFiReconnect = 0;
const unsigned long WIFI_RECONNECT_INTERVAL = 30000;

// ==================== 函式宣告 ====================
void updateDisplay();
void drawStatusIcons();
bool isWiFiReallyConnected();
bool connectWiFiWithProgress();
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
void fetchWeatherData();
void handleEncoder();
void handleEncoderPush();
void handleButtons();
const char* getWeatherIcon(const String &desc);
void sendBoxStatus();
void sendHistoricData();
void sendHistoricDataEnd();

// ==================== Wi-Fi 狀態校正 ====================
bool isWiFiReallyConnected() {
    return (WiFi.status() == WL_CONNECTED) &&
           (WiFi.localIP() != IPAddress(0,0,0,0)) &&
           (WiFi.RSSI() != 0);
}

// ==================== Wi-Fi 連線 + 進度顯示 ====================
bool connectWiFiWithProgress() {
    preferences.begin("wifi", true);
    String ssid = preferences.getString("ssid", default_ssid);
    String pass = preferences.getString("pass", default_password);
    preferences.end();

    Serial.printf("Connecting to WiFi: %s\n", ssid.c_str());

    WiFi.mode(WIFI_STA);
    WiFi.persistent(false);
    WiFi.begin(ssid.c_str(), pass.c_str());

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(10, 20, "WiFi Connecting...");
    u8g2.drawStr(10, 35, ssid.c_str());
    u8g2.sendBuffer();

    int dots = 0;
    unsigned long startTime = millis();
    while (!isWiFiReallyConnected() && millis() - startTime < 20000) {
        delay(500);
        dots = (dots + 1) % 4;
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Connecting");
        char dotStr[5] = "...";
        dotStr[dots] = '\0';
        u8g2.drawStr(85, 20, dotStr);
        u8g2.drawStr(10, 35, ssid.c_str());
        u8g2.sendBuffer();
    }

    if (isWiFiReallyConnected()) {
        Serial.printf("WiFi Connected! IP: %s\n", WiFi.localIP().toString().c_str());
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Connected!");
        u8g2.drawStr(10, 35, WiFi.localIP().toString().c_str());
        u8g2.sendBuffer();
        delay(1500);
        return true;
    } else {
        Serial.println("WiFi Connection Failed");
        u8g2.clearBuffer();
        u8g2.drawStr(10, 20, "WiFi Failed");
        u8g2.drawStr(10, 35, "Check SSID/PASS");
        u8g2.sendBuffer();
        delay(2000);
        return false;
    }
}

// ==================== NTP 強制同步 ====================
void syncTimeNTPForce() {
    if (!isWiFiReallyConnected()) return;

    Serial.println("NTP Sync Start...");
    bool synced = false;

    for (int i = 0; i < numNTPServers && !synced; i++) {
        Serial.printf("Trying NTP: %s\n", ntpServers[i]);
        configTime(GMT_OFFSET, DAYLIGHT_OFFSET, ntpServers[i]);

        struct tm timeinfo;
        int retry = 0;
        while (!getLocalTime(&timeinfo) && retry < 25) {
            delay(200);
            retry++;
        }

        if (retry < 25) {
            Serial.printf("NTP Synced: %s\n", ntpServers[i]);
            syncIconStartTime = millis();
            syncInProgress = true;
            lastNTPResync = millis();
            synced = true;
        }
    }

    if (!synced) {
        Serial.println("All NTP Failed");
    }
}

// ==================== 歷史記錄 ====================
void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) return;
        DataPoint emptyPoint = {0.0, 0.0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) {
            file.write((uint8_t*)&emptyPoint, sizeof(DataPoint));
        }
        file.close();
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
    File file = SPIFFS.open("/history.dat", "r+");
    if (!file) return;

    DataPoint newDataPoint = {temp, hum, rssi};
    long pos = historyIndex * sizeof(DataPoint);
    file.seek(pos);
    file.write((uint8_t*)&newDataPoint, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    saveHistoryMetadata();
}

void loadHistoryWindow(int offset) {
    int pointsToRead = min(historyCount, HISTORY_WINDOW_SIZE);
    if (pointsToRead == 0) return;

    File file = SPIFFS.open("/history.dat", "r");
    if (!file) return;

    int startIdx = (historyIndex - offset - pointsToRead + MAX_HISTORY * 2) % MAX_HISTORY;

    for (int i = 0; i < pointsToRead; i++) {
        int currentIdx = (startIdx + i) % MAX_HISTORY;
        long pos = currentIdx * sizeof(DataPoint);
        file.seek(pos);
        file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
}

// ==================== BLE 回呼 ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleDeviceConnected = true;
        Serial.println("BLE Connected");
        delay(500);
        pServer->getAdvertising()->start();
    }
    void onDisconnect(BLEServer* pServer) {
        bleDeviceConnected = false;
        Serial.println("BLE Disconnected");
        delay(500);
        pServer->getAdvertising()->start();
    }
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
                syncInProgress = true;
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
                    WiFi.disconnect(true);
                    delay(1000);
                    connectWiFiWithProgress();
                    sendTimeSyncAck();
                }
            }
            break;

        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                // engineeringMode = (data[1] == 0x01);
                sendTimeSyncAck();
            }
            break;

        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC: sendHistoricData(); break;
        default: sendErrorReport(0x03); break;
    }
}

void sendTimeSyncAck() {
    if (!bleDeviceConnected) return;
    uint8_t ack[1] = {CMD_TIME_SYNC_ACK};
    pDataEventCharacteristic->setValue(ack, 1);
    pDataEventCharacteristic->notify();
}

void sendErrorReport(uint8_t errorCode) {
    if (!bleDeviceConnected) return;
    uint8_t err[2] = {CMD_ERROR, errorCode};
    pDataEventCharacteristic->setValue(err, 2);
    pDataEventCharacteristic->notify();
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    uint8_t status[2] = {CMD_REPORT_STATUS, 0x00};
    pDataEventCharacteristic->setValue(status, 2);
    pDataEventCharacteristic->notify();
}

void sendSensorDataReport() {
    if (!bleDeviceConnected) return;
    float temp = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    float hum = dht.readHumidity();
    if (isnan(temp) || isnan(hum)) return;

    uint8_t report[5] = {CMD_REPORT_ENV};
    report[1] = (int)temp;
    report[2] = (int)((temp - (int)temp) * 100);
    report[3] = (int)hum;
    report[4] = (int)((hum - (int)hum) * 100);
    pDataEventCharacteristic->setValue(report, 5);
    pDataEventCharacteristic->notify();
}

void sendHistoricData() {
    if (!bleDeviceConnected || historyCount == 0) {
        uint8_t end[1] = {CMD_REPORT_HISTORIC_END};
        pDataEventCharacteristic->setValue(end, 1);
        pDataEventCharacteristic->notify();
        return;
    }
    uint8_t end[1] = {CMD_REPORT_HISTORIC_END};
    pDataEventCharacteristic->setValue(end, 1);
    pDataEventCharacteristic->notify();
}

// ==================== UI 核心 ====================
void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    switch (currentPageIndex) {
        case SCREEN_TIME: drawTimeScreen(); break;
        case SCREEN_DATE: drawDateScreen(); break;
        case SCREEN_WEATHER: drawWeatherScreen(); break;
        case SCREEN_SENSOR: drawSensorScreen(); break;
        case SCREEN_TEMP_CHART: drawTempChartScreen(); break;
        case SCREEN_HUM_CHART: drawHumChartScreen(); break;
        case SCREEN_RSSI_CHART: drawRssiChartScreen(); break;
        case SCREEN_SYSTEM: drawSystemScreen(); break;
    }

    drawStatusIcons();
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentPageIndex != SCREEN_TIME) return;

    u8g2.setFont(u8g2_font_open_iconic_all_1x_t);
    int x = 0;
    const int spacing = 12;

    if (bleDeviceConnected) {
        u8g2.drawStr(x, 10, "d");
        x += spacing;
    }

    if (syncInProgress && (millis() - syncIconStartTime < SYNC_ICON_DURATION) && (millis() / 500) % 2 == 0) {
        u8g2.drawStr(x, 10, "Q");
        x += spacing;
    }

    if (syncInProgress && millis() - syncIconStartTime >= SYNC_ICON_DURATION) {
        syncInProgress = false;
    }

    u8g2.drawStr(118, 10, isWiFiReallyConnected() ? "W" : "!");
}

// ==================== 圖表 ====================
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);

    if (historyCount < 2) {
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(10, 35, "No Data");
        return;
    }

    loadHistoryWindow(historyViewOffset);

    int displayCount = min((int)HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) {
        u8g2.drawStr(10, 35, "Insufficient Data");
        return;
    }

    float minVal = isRssi ? 0 : 101;
    float maxVal = isRssi ? -100 : -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? (float)historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }

    float range = maxVal - minVal;
    if (isRssi) {
        minVal = max(minVal, -90.0f);
        maxVal = min(maxVal, -30.0f);
        range = maxVal - minVal;
        if (range < 10.0f) range = 10.0f;
    } else if (range < 1.0f) {
        minVal -= 0.5f; maxVal += 0.5f;
        range = maxVal - minVal;
    }
    if (range < 0.1f) range = 1.0f;

    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr);
    char buf[10];
    sprintf(buf, isRssi ? "%d" : (isTemp ? "%.1f" : "%.0f"), maxVal);
    u8g2.drawStr(0, chartY + 5, buf);
    sprintf(buf, isRssi ? "%d" : (isTemp ? "%.1f" : "%.0f"), minVal);
    u8g2.drawStr(0, chartY + chartH, buf);

    u8g2.drawFrame(chartX, chartY, chartW, chartH);

    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? (float)historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        int x = chartX + (i * chartW / displayCount);
        int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; lastY = y;
    }

    char countStr[20];
    sprintf(countStr, "[%d/%d]", displayCount, historyCount);
    u8g2.drawStr(128 - u8g2.getStrWidth(countStr) - 2, 10, countStr);

    char offsetStr[10];
    if (historyViewOffset == 0) strcpy(offsetStr, "Now");
    else {
        float hours = (historyViewOffset * historyRecordInterval) / 3600000.0;
        sprintf(offsetStr, "-%.1fh", hours);
    }
    u8g2.drawStr(chartX + 2, chartY + chartH - 2, offsetStr);

    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        u8g2.drawStr(100, 64, "VIEW");
    }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart (dBm)", false, true); }

// ==================== 其他畫面 ====================
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) {
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr(10, 32, "Time not set");
    } else {
        struct tm * ptm = localtime(&now);
        u8g2.setFont(u8g2_font_fub20_tn);
        char timeStr[9];
        sprintf(timeStr, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec);
        u8g2.drawStr((128 - u8g2.getStrWidth(timeStr)) / 2, 42, timeStr);
    }
}

void drawDateScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); return; }
    struct tm * ptm = localtime(&now);
    const char* weekAbbr[] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
    const char* monthAbbr[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

    char dayStr[4], yearStr[6];
    sprintf(dayStr, "%02d", ptm->tm_mday);
    sprintf(yearStr, "%d", ptm->tm_year + 1900);

    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, dayStr);
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, weekAbbr[ptm->tm_wday]);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(72, 40, monthAbbr[ptm->tm_mon]);
    u8g2.drawStr(72, 56, yearStr);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr(0, 12, city.c_str());
    if (!isWiFiReallyConnected()) {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return;
    }
    if (weatherData.valid) {
        char buffer[20];
        const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); u8g2.drawStr(5, 50, icon);

        u8g2.setFont(u8g2_font_fub25_tn);
        sprintf(buffer, "%.1f", weatherData.temp);
        u8g2.drawStr(45, 32, buffer);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buffer) + 4, 15, 3);

        u8g2.setFont(u8g2_font_unifont_t_chinese2); u8g2.drawStr(45, 50, weatherData.description.c_str());

        u8g2.setFont(u8g2_font_ncenR10_tr); sprintf(buffer, "H:%d%%", weatherData.humidity); u8g2.drawStr(45, 64, buffer);
    } else {
        u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "No data");
    }
}

void drawSensorScreen() {
    float h = dht.readHumidity();
    float t = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
    if (isnan(h) || isnan(t)) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 40, "Sensor Error!"); return; }
    char buffer[20];
    u8g2.setFont(u8g2_font_helvB10_tr); u8g2.drawStr((128 - u8g2.getStrWidth("INDOOR")) / 2, 12, "INDOOR");
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 38, "TEMP");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buffer, "%.1f C", t); u8g2.drawStr(128 - u8g2.getStrWidth(buffer) - 10, 38, buffer);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 62, "HUMI");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buffer, "%.0f %%", h); u8g2.drawStr(128 - u8g2.getStrWidth(buffer) - 10, 62, buffer);
}

void drawSystemScreen() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 12, "System Info");
    u8g2.setFont(u8g2_font_profont11_tf);

    int y = 28;

    String ssid = WiFi.SSID(); if (ssid == "") ssid = "N/A";
    if (isWiFiReallyConnected()) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y+=12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y+=12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y+=12;
    } else {
        u8g2.drawStr(0, y, "WiFi Disconnected"); y+=12;
    }

    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap() / 1024) + " KB").c_str()); y+=12;
    u8g2.drawStr(0, y, ("Up: " + String(millis()/60000) + " min").c_str());
}

const char* getWeatherIcon(const String &desc) {
    String s = desc; s.toLowerCase();
    if (s.indexOf("clear") >= 0 || s.indexOf("晴") >= 0) return "A";
    if (s.indexOf("cloud") >= 0 || s.indexOf("雲") >= 0 || s.indexOf("阴") >= 0) return "C";
    if (s.indexOf("rain") >= 0 || s.indexOf("雨") >= 0) return "R";
    if (s.indexOf("snow") >= 0 || s.indexOf("雪") >= 0) return "S";
    if (s.indexOf("thunder") >= 0 || s.indexOf("雷") >= 0) return "T";
    if (s.indexOf("fog") >= 0 || s.indexOf("霧") >= 0 || s.indexOf("霾") >= 0) return "M";
    return "C";
}

void fetchWeatherData() {
    if (!isWiFiReallyConnected()) { weatherData.valid = false; return; }
    HTTPClient http;
    String serverPath = "http://api.openweathermap.org/data/2.5/weather?q=" + city + "," +
                        countryCode + "&units=metric&lang=zh_tw&APPID=" + openWeatherMapApiKey;
    http.begin(serverPath.c_str());
    int httpResponseCode = http.GET();

    if (httpResponseCode == HTTP_CODE_OK) {
        String payload = http.getString();
        int descStart = payload.indexOf("\"description\":\"") + 15;
        int descEnd = payload.indexOf("\"", descStart);
        if(descStart > 14 && descEnd > descStart) weatherData.description = payload.substring(descStart, descEnd);
        int tempStart = payload.indexOf("\"temp\":") + 7;
        int tempEnd = payload.indexOf(",", tempStart);
        if(tempStart > 6 && tempEnd > tempStart) weatherData.temp = payload.substring(tempStart, tempEnd).toFloat();
        int humStart = payload.indexOf("\"humidity\":") + 11;
        int humEnd = payload.indexOf("}", humStart);
        if(humStart > 10 && humEnd > humStart) weatherData.humidity = payload.substring(humStart, humEnd).toInt();
        weatherData.valid = true;
    } else {
        weatherData.valid = false;
    }
    http.end();
}

// ==================== 旋鈕與按鈕 ====================
void handleEncoder() {
    if (rotaryEncoder.encoderChanged()) {
        if (currentEncoderMode == MODE_VIEW_ADJUST) {
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

        ScreenState currentScreen = currentPageIndex;

        if(currentScreen == SCREEN_TEMP_CHART || currentScreen == SCREEN_HUM_CHART || currentScreen == SCREEN_RSSI_CHART){
            int idx = (currentScreen == SCREEN_TEMP_CHART) ? 0 : (currentScreen == SCREEN_HUM_CHART) ? 1 : 2;

            if (currentEncoderMode == MODE_NAVIGATION) {
                currentEncoderMode = MODE_VIEW_ADJUST;
                historyViewOffset = lastViewOffset[idx];
                int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
                rotaryEncoder.setBoundaries(0, maxOffset, false);
                rotaryEncoder.setEncoderValue(historyViewOffset);
            } else {
                lastViewOffset[idx] = historyViewOffset;
                currentEncoderMode = MODE_NAVIGATION;
                rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
                rotaryEncoder.setEncoderValue((long)currentScreen);
            }
        }
        updateDisplay();
    }
}

void handleButtons() {
    const unsigned long DEBOUNCE_DELAY = 300;
    if (digitalRead(BUTTON_CONFIRM_PIN) == LOW) {
        if (millis() - lastConfirmPressTime > DEBOUNCE_DELAY) {
            lastConfirmPressTime = millis();

            if (currentEncoderMode != MODE_NAVIGATION) {
                ScreenState currentScreen = currentPageIndex;
                if(currentScreen == SCREEN_TEMP_CHART || currentScreen == SCREEN_HUM_CHART || currentScreen == SCREEN_RSSI_CHART){
                    int idx = (currentScreen == SCREEN_TEMP_CHART) ? 0 : (currentScreen == SCREEN_HUM_CHART) ? 1 : 2;
                    lastViewOffset[idx] = historyViewOffset;
                }
                currentEncoderMode = MODE_NAVIGATION;
                rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
            }
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
            currentPageIndex = SCREEN_TIME;
            updateDisplay();
        }
    }
}

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n=== SmartMedBox v16.4 ===");

    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    delay(100);
    u8g2.begin();
    u8g2.enableUTF8Print();
    dht.begin();

    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS Failed");
        return;
    }
    initializeHistoryFile();

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
    loadHistoryMetadata();

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr(20, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(50, 45, "v16.4");
    u8g2.sendBuffer();
    delay(1500);

    BLEDevice::init("SmartMedBox");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic *pCommandChar = pService->createCharacteristic(COMMAND_CHANNEL_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommandChar->setCallbacks(new CommandCallbacks());
    pDataEventCharacteristic = pService->createCharacteristic(DATA_EVENT_CHANNEL_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pDataEventCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
    Serial.println("BLE OK");

    if (connectWiFiWithProgress()) {
        syncTimeNTPForce();
        fetchWeatherData();
    }

    currentPageIndex = SCREEN_TIME;
    rotaryEncoder.setEncoderValue(SCREEN_TIME);
}

// ==================== LOOP ====================
void loop() {
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (!isWiFiReallyConnected() && millis() - lastWiFiReconnect > WIFI_RECONNECT_INTERVAL) {
        lastWiFiReconnect = millis();
        connectWiFiWithProgress();
    }

    if (millis() - lastDisplayUpdate > displayInterval) {
        updateDisplay();
    }

    if (millis() - lastHistoryRecord > historyRecordInterval) {
        float temp = dht.readTemperature() - TEMP_CALIBRATION_OFFSET;
        float hum = dht.readHumidity();
        int16_t rssi = WiFi.RSSI();
        if (!isnan(temp) && !isnan(hum)) {
            addDataToHistory(temp, hum, rssi);
        }
        lastHistoryRecord = millis();
    }

    if (isWiFiReallyConnected() && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
        syncTimeNTPForce();
    }

    if (isWiFiReallyConnected() && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }
}