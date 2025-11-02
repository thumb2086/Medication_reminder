/*
  SmartMedBox Firmware v16.0
  硬體: ESP32-C6
  IDE: esp32 by Espressif Systems v3.0.0+
  板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

  更新內容:
  - 新增 Wi-Fi 連線進度畫面、成功畫面、失敗畫面
  - 新增自動 Wi-Fi 重連機制 (每 30 秒檢查，斷線時顯示進度畫面)
  - 新增連線進度條
  - 圖示系統僅在時鐘頁面顯示，多個圖示一字排開
  - 新增工程模式 (透過 BLE 指令 0x13 啟用)，啟用後可查看圖表與 Wi-Fi 連線資訊
  - 工程模式專屬圖示 (使用 'G' 作為工具圖示)
  - 溫濕度資料使用 SPIFFS 永久化儲存，斷電不消失
  - 圖示調整: dd (BLE), QQ (同步閃爍), WW (Wi-Fi 連線), !! (Wi-Fi 斷線), G (工程模式)
  - 其他修復與優化
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

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4;  // 預設非工程模式，只有前 4 個畫面

ScreenState currentPageIndex = SCREEN_TIME;
static int lastViewOffset[3] = {0, 0, 0}; // TEMP, HUM, RSSI

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
unsigned long lastWiFiCheck = 0;
const unsigned long WIFI_CHECK_INTERVAL = 30000;

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
void connectWiFi(bool showScreen);
void drawWiFiConnecting(String ssid, int progress, int dotsCount);
void drawWiFiConnected(String ip);
void drawWiFiFailed();

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
                    connectWiFi(true);  // 重連並顯示畫面
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

// ==================== Wi-Fi 連線函式 ====================
void connectWiFi(bool showScreen) {
    preferences.begin("wifi", true);
    String savedSSID = preferences.getString("ssid", default_ssid);
    String savedPASS = preferences.getString("pass", default_password);
    preferences.end();

    WiFi.disconnect(true);
    delay(100);
    WiFi.begin(savedSSID.c_str(), savedPASS.c_str());

    int attempts = 60;
    bool connected = false;
    for (int i = 0; i < attempts; i++) {
        if (showScreen) {
            int dots = (i % 3) + 1;
            drawWiFiConnecting(savedSSID, i, dots);
        }
        if (WiFi.status() == WL_CONNECTED) {
            connected = true;
            break;
        }
        delay(500);
    }

    if (connected) {
        if (showScreen) {
            drawWiFiConnected(WiFi.localIP().toString());
            delay(2000);
        }
        syncTimeNTPForce();
        fetchWeatherData();
    } else {
        if (showScreen) {
            drawWiFiFailed();
            delay(2000);
        }
    }
}

void drawWiFiConnecting(String ssid, int progress, int dotsCount) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    String text = "WiFi Connecting";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 20, text.c_str());

    String dots = "";
    for (int i = 0; i < dotsCount; i++) dots += ".";
    text = dots;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 32, text.c_str());

    text = "SSID: " + ssid;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 44, text.c_str());

    // 進度條
    int barWidth = (progress * 100) / 60;
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

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- SmartMedBox Firmware v16.0 ---");

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

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);

    loadHistoryMetadata();

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("v16.0"))/2, 45, "v16.0");
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

    connectWiFi(true);  // 開機連線並顯示畫面

    float t = dht.readTemperature();
    float h = dht.readHumidity();
    int16_t rssi = WiFi.RSSI();
    if (!isnan(t) && !isnan(h)) {
        addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi);
    }

    currentPageIndex = SCREEN_TIME;
    rotaryEncoder.setEncoderValue(SCREEN_TIME);

    updateScreens();  // 初始化畫面數量

    Serial.println("--- Setup Complete ---\n");
}

// ==================== LOOP ====================
void loop() {
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (WiFi.status() == WL_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
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

    if (millis() - lastWeatherUpdate > WEATHER_INTERVAL && WiFi.status() == WL_CONNECTED) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }

    if (millis() - lastWiFiCheck > WIFI_CHECK_INTERVAL) {
        lastWiFiCheck = millis();
        if (WiFi.status() != WL_CONNECTED) {
            connectWiFi(true);  // 斷線時重連並顯示畫面
        }
    }

    if (millis() - lastDisplayUpdate >= displayInterval) {
        updateDisplay();
    }
}

// ==================== UI 核心函式 ====================

void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    switch (currentPageIndex) {
        case SCREEN_TIME: drawTimeScreen(); break;
        case SCREEN_DATE: drawDateScreen(); break;
        case SCREEN_WEATHER: drawWeatherScreen(); break;
        case SCREEN_SENSOR: drawSensorScreen(); break;
        case SCREEN_TEMP_CHART: if (isEngineeringMode) drawTempChartScreen(); break;
        case SCREEN_HUM_CHART: if (isEngineeringMode) drawHumChartScreen(); break;
        case SCREEN_RSSI_CHART: if (isEngineeringMode) drawRssiChartScreen(); break;
        case SCREEN_SYSTEM: if (isEngineeringMode) drawSystemScreen(); break;
    }

    drawStatusIcons();
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentPageIndex != SCREEN_TIME) return;  // 只在時鐘頁面顯示

    u8g2.setFont(u8g2_font_open_iconic_all_1x_t);
    int x = 0;
    const int spacing = 10;  // 調整間距以容納多個圖示

    if (bleDeviceConnected) {
        u8g2.drawGlyph(x, 10, 'd'); x += spacing;  // BLE 圖示 (假設 'd' 為藍牙圖示)
        // 為 dd 顯示兩個
        u8g2.drawGlyph(x, 10, 'd'); x += spacing;
    }

    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) {
        u8g2.drawGlyph(x, 10, 'Q'); x += spacing;
        u8g2.drawGlyph(x, 10, 'Q'); x += spacing;  // QQ 閃爍
    }

    if (WiFi.status() == WL_CONNECTED) {
        u8g2.drawGlyph(x, 10, 'W'); x += spacing;
        u8g2.drawGlyph(x, 10, 'W'); x += spacing;  // WW Wi-Fi 連線
    } else {
        u8g2.drawGlyph(x, 10, '!'); x += spacing;
        u8g2.drawGlyph(x, 10, '!'); x += spacing;  // !! Wi-Fi 斷線
    }

    if (isEngineeringMode) {
        u8g2.drawGlyph(x, 10, 'G'); x += spacing;  // 工程模式圖示 (假設 'G' 為工具圖示)
    }
}

void updateScreens() {
    NUM_SCREENS = isEngineeringMode ? 8 : 4;
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
    if (currentPageIndex >= NUM_SCREENS) {
        currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(SCREEN_TIME);
    }
    updateDisplay();
}

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
        ScreenState current = currentPageIndex;
        int idx = (current == SCREEN_TEMP_CHART) ? 0 : (current == SCREEN_HUM_CHART) ? 1 : 2;

        if (currentEncoderMode == MODE_NAVIGATION &&
            (current == SCREEN_TEMP_CHART || current == SCREEN_HUM_CHART || current == SCREEN_RSSI_CHART) && isEngineeringMode) {
            currentEncoderMode = MODE_VIEW_ADJUST;
            historyViewOffset = lastViewOffset[idx];
            int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
            rotaryEncoder.setBoundaries(0, maxOffset, false);
            rotaryEncoder.setEncoderValue(historyViewOffset);
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
        currentEncoderMode = MODE_NAVIGATION;
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(SCREEN_TIME);
        updateDisplay();
    }
}

void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) return;
        DataPoint empty = {0, 0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) {
            file.write((uint8_t*)&empty, sizeof(DataPoint));
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
    DataPoint dp = {temp, hum, rssi};
    file.seek(historyIndex * sizeof(DataPoint));
    file.write((uint8_t*)&dp, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    saveHistoryMetadata();

    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
        rotaryEncoder.setBoundaries(0, maxOffset, false);
    }
}

void loadHistoryWindow(int offset) {
    int points = min(historyCount, HISTORY_WINDOW_SIZE);
    if (points == 0) return;
    File file = SPIFFS.open("/history.dat", "r");
    if (!file) return;
    int startIdx = (historyIndex - offset - points + MAX_HISTORY) % MAX_HISTORY;
    for (int i = 0; i < points; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint));
        file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
}

// ==================== 圖表繪製 ====================
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);

    if (historyCount < 2) {
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(10, 35, "No Data");
        return;
    }

    loadHistoryWindow(historyViewOffset);
    int displayCount = min(HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) {
        u8g2.drawStr(10, 35, "Insufficient Data");
        return;
    }

    // 初始化範圍
    float minVal = 999, maxVal = -999;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }

    // RSSI 特殊處理
    if (isRssi) {
        minVal = max(minVal, -100.0f);
        maxVal = min(maxVal, -30.0f);
        if (maxVal - minVal < 10) {
            float mid = (minVal + maxVal) / 2;
            minVal = mid - 5;
            maxVal = mid + 5;
        }
    } else if (isTemp && maxVal - minVal < 1) {
        float mid = (minVal + maxVal) / 2;
        minVal = mid - 0.5;
        maxVal = mid + 0.5;
    } else if (!isTemp && maxVal - minVal < 2) {
        float mid = (minVal + maxVal) / 2;
        minVal = mid - 1;
        maxVal = mid + 1;
    }

    float range = maxVal - minVal;
    if (range < 0.1) range = 1;

    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr);

    // Y 軸標籤 + 單位
    char buf[12];
    if (isRssi) {
        sprintf(buf, "%d dBm", (int)maxVal);
        u8g2.drawStr(0, chartY + 5, buf);
        sprintf(buf, "%d dBm", (int)minVal);
        u8g2.drawStr(0, chartY + chartH, buf);
    } else {
        sprintf(buf, isTemp ? "%.1f" : "%.0f", maxVal);
        u8g2.drawStr(0, chartY + 5, buf);
        sprintf(buf, isTemp ? "%.1f" : "%.0f", minVal);
        u8g2.drawStr(0, chartY + chartH, buf);
    }

    u8g2.drawFrame(chartX, chartY, chartW, chartH);

    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        int x = chartX + (i * chartW / displayCount);
        int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; lastY = y;
    }

    // 筆數
    char countStr[20];
    sprintf(countStr, "[%d/%d]", displayCount, historyCount);
    int countW = u8g2.getStrWidth(countStr);
    u8g2.drawStr(128 - countW - 2, 10, countStr);

    // Now / -X.Xh
    char offsetStr[10];
    if (historyViewOffset == 0) {
        strcpy(offsetStr, "Now");
    } else {
        float hours = (historyViewOffset * historyRecordInterval) / 3600000.0;
        sprintf(offsetStr, "-%.1fh", hours);
    }
    int offsetW = u8g2.getStrWidth(offsetStr);
    u8g2.drawStr(128 - offsetW - 2, chartY + chartH - 2, offsetStr);

    // VIEW 標籤
    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        u8g2.drawStr(100, 64, "VIEW");
    }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

// ==================== 其他畫面 ====================
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); }
    else { struct tm *ptm = localtime(&now); u8g2.setFont(u8g2_font_fub20_tn); char s[9]; sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec); u8g2.drawStr((128 - u8g2.getStrWidth(s))/2, 42, s); }
}

void drawDateScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); return; }
    struct tm *ptm = localtime(&now);
    const char* week[] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
    const char* month[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
    char day[4], year[6]; sprintf(day, "%02d", ptm->tm_mday); sprintf(year, "%d", ptm->tm_year + 1900);
    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, day);
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, week[ptm->tm_wday]);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(72, 40, month[ptm->tm_mon]); u8g2.drawStr(72, 56, year);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr(0, 12, city.c_str());
    if (WiFi.status() != WL_CONNECTED) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return; }
    if (weatherData.valid) {
        char buf[20]; const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); u8g2.drawStr(5, 50, icon);
        u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.1f", weatherData.temp); u8g2.drawStr(45, 32, buf);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buf) + 4, 15, 3);
        u8g2.setFont(u8g2_font_unifont_t_chinese2); u8g2.drawStr(45, 50, weatherData.description.c_str());
        u8g2.setFont(u8g2_font_ncenR10_tr); sprintf(buf, "H:%d%%", weatherData.humidity); u8g2.drawStr(45, 64, buf);
    } else { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "No data"); }
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
    if (WiFi.status() == WL_CONNECTED) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y += 12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y += 12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y += 12;
    } else { u8g2.drawStr(0, y, "WiFi Disconnected"); y += 12; }
    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap()/1024) + " KB").c_str()); y += 12;
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
}/*
  SmartMedBox Firmware v16.0
  硬體: ESP32-C6
  IDE: esp32 by Espressif Systems v3.0.0+
  板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

  更新內容:
  - 新增 Wi-Fi 連線進度畫面、成功畫面、失敗畫面
  - 新增自動 Wi-Fi 重連機制 (每 30 秒檢查，斷線時顯示進度畫面)
  - 新增連線進度條
  - 圖示系統僅在時鐘頁面顯示，多個圖示一字排開
  - 新增工程模式 (透過 BLE 指令 0x13 啟用)，啟用後可查看圖表與 Wi-Fi 連線資訊
  - 工程模式專屬圖示 (使用 'G' 作為工具圖示)
  - 溫濕度資料使用 SPIFFS 永久化儲存，斷電不消失
  - 圖示調整: dd (BLE), QQ (同步閃爍), WW (Wi-Fi 連線), !! (Wi-Fi 斷線), G (工程模式)
  - 其他修復與優化
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

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4;  // 預設非工程模式，只有前 4 個畫面

ScreenState currentPageIndex = SCREEN_TIME;
static int lastViewOffset[3] = {0, 0, 0}; // TEMP, HUM, RSSI

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
unsigned long lastWiFiCheck = 0;
const unsigned long WIFI_CHECK_INTERVAL = 30000;

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
void connectWiFi(bool showScreen);
void drawWiFiConnecting(String ssid, int progress, int dotsCount);
void drawWiFiConnected(String ip);
void drawWiFiFailed();

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
                    connectWiFi(true);  // 重連並顯示畫面
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

// ==================== Wi-Fi 連線函式 ====================
void connectWiFi(bool showScreen) {
    preferences.begin("wifi", true);
    String savedSSID = preferences.getString("ssid", default_ssid);
    String savedPASS = preferences.getString("pass", default_password);
    preferences.end();

    WiFi.disconnect(true);
    delay(100);
    WiFi.begin(savedSSID.c_str(), savedPASS.c_str());

    int attempts = 60;
    bool connected = false;
    for (int i = 0; i < attempts; i++) {
        if (showScreen) {
            int dots = (i % 3) + 1;
            drawWiFiConnecting(savedSSID, i, dots);
        }
        if (WiFi.status() == WL_CONNECTED) {
            connected = true;
            break;
        }
        delay(500);
    }

    if (connected) {
        if (showScreen) {
            drawWiFiConnected(WiFi.localIP().toString());
            delay(2000);
        }
        syncTimeNTPForce();
        fetchWeatherData();
    } else {
        if (showScreen) {
            drawWiFiFailed();
            delay(2000);
        }
    }
}

void drawWiFiConnecting(String ssid, int progress, int dotsCount) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    String text = "WiFi Connecting";
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 20, text.c_str());

    String dots = "";
    for (int i = 0; i < dotsCount; i++) dots += ".";
    text = dots;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 32, text.c_str());

    text = "SSID: " + ssid;
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 44, text.c_str());

    // 進度條
    int barWidth = (progress * 100) / 60;
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

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- SmartMedBox Firmware v16.0 ---");

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

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);

    loadHistoryMetadata();

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("v16.0"))/2, 45, "v16.0");
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

    connectWiFi(true);  // 開機連線並顯示畫面

    float t = dht.readTemperature();
    float h = dht.readHumidity();
    int16_t rssi = WiFi.RSSI();
    if (!isnan(t) && !isnan(h)) {
        addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi);
    }

    currentPageIndex = SCREEN_TIME;
    rotaryEncoder.setEncoderValue(SCREEN_TIME);

    updateScreens();  // 初始化畫面數量

    Serial.println("--- Setup Complete ---\n");
}

// ==================== LOOP ====================
void loop() {
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (WiFi.status() == WL_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
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

    if (millis() - lastWeatherUpdate > WEATHER_INTERVAL && WiFi.status() == WL_CONNECTED) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }

    if (millis() - lastWiFiCheck > WIFI_CHECK_INTERVAL) {
        lastWiFiCheck = millis();
        if (WiFi.status() != WL_CONNECTED) {
            connectWiFi(true);  // 斷線時重連並顯示畫面
        }
    }

    if (millis() - lastDisplayUpdate >= displayInterval) {
        updateDisplay();
    }
}

// ==================== UI 核心函式 ====================

void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    switch (currentPageIndex) {
        case SCREEN_TIME: drawTimeScreen(); break;
        case SCREEN_DATE: drawDateScreen(); break;
        case SCREEN_WEATHER: drawWeatherScreen(); break;
        case SCREEN_SENSOR: drawSensorScreen(); break;
        case SCREEN_TEMP_CHART: if (isEngineeringMode) drawTempChartScreen(); break;
        case SCREEN_HUM_CHART: if (isEngineeringMode) drawHumChartScreen(); break;
        case SCREEN_RSSI_CHART: if (isEngineeringMode) drawRssiChartScreen(); break;
        case SCREEN_SYSTEM: if (isEngineeringMode) drawSystemScreen(); break;
    }

    drawStatusIcons();
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentPageIndex != SCREEN_TIME) return;  // 只在時鐘頁面顯示

    u8g2.setFont(u8g2_font_open_iconic_all_1x_t);
    int x = 0;
    const int spacing = 10;  // 調整間距以容納多個圖示

    if (bleDeviceConnected) {
        u8g2.drawGlyph(x, 10, 'd'); x += spacing;  // BLE 圖示 (假設 'd' 為藍牙圖示)
        // 為 dd 顯示兩個
        u8g2.drawGlyph(x, 10, 'd'); x += spacing;
    }

    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) {
        u8g2.drawGlyph(x, 10, 'Q'); x += spacing;
        u8g2.drawGlyph(x, 10, 'Q'); x += spacing;  // QQ 閃爍
    }

    if (WiFi.status() == WL_CONNECTED) {
        u8g2.drawGlyph(x, 10, 'W'); x += spacing;
        u8g2.drawGlyph(x, 10, 'W'); x += spacing;  // WW Wi-Fi 連線
    } else {
        u8g2.drawGlyph(x, 10, '!'); x += spacing;
        u8g2.drawGlyph(x, 10, '!'); x += spacing;  // !! Wi-Fi 斷線
    }

    if (isEngineeringMode) {
        u8g2.drawGlyph(x, 10, 'G'); x += spacing;  // 工程模式圖示 (假設 'G' 為工具圖示)
    }
}

void updateScreens() {
    NUM_SCREENS = isEngineeringMode ? 8 : 4;
    rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
    if (currentPageIndex >= NUM_SCREENS) {
        currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(SCREEN_TIME);
    }
    updateDisplay();
}

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
        ScreenState current = currentPageIndex;
        int idx = (current == SCREEN_TEMP_CHART) ? 0 : (current == SCREEN_HUM_CHART) ? 1 : 2;

        if (currentEncoderMode == MODE_NAVIGATION &&
            (current == SCREEN_TEMP_CHART || current == SCREEN_HUM_CHART || current == SCREEN_RSSI_CHART) && isEngineeringMode) {
            currentEncoderMode = MODE_VIEW_ADJUST;
            historyViewOffset = lastViewOffset[idx];
            int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
            rotaryEncoder.setBoundaries(0, maxOffset, false);
            rotaryEncoder.setEncoderValue(historyViewOffset);
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
        currentEncoderMode = MODE_NAVIGATION;
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        currentPageIndex = SCREEN_TIME;
        rotaryEncoder.setEncoderValue(SCREEN_TIME);
        updateDisplay();
    }
}

void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) return;
        DataPoint empty = {0, 0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) {
            file.write((uint8_t*)&empty, sizeof(DataPoint));
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
    DataPoint dp = {temp, hum, rssi};
    file.seek(historyIndex * sizeof(DataPoint));
    file.write((uint8_t*)&dp, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    saveHistoryMetadata();

    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
        rotaryEncoder.setBoundaries(0, maxOffset, false);
    }
}

void loadHistoryWindow(int offset) {
    int points = min(historyCount, HISTORY_WINDOW_SIZE);
    if (points == 0) return;
    File file = SPIFFS.open("/history.dat", "r");
    if (!file) return;
    int startIdx = (historyIndex - offset - points + MAX_HISTORY) % MAX_HISTORY;
    for (int i = 0; i < points; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint));
        file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
}

// ==================== 圖表繪製 ====================
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);

    if (historyCount < 2) {
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(10, 35, "No Data");
        return;
    }

    loadHistoryWindow(historyViewOffset);
    int displayCount = min(HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) {
        u8g2.drawStr(10, 35, "Insufficient Data");
        return;
    }

    // 初始化範圍
    float minVal = 999, maxVal = -999;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }

    // RSSI 特殊處理
    if (isRssi) {
        minVal = max(minVal, -100.0f);
        maxVal = min(maxVal, -30.0f);
        if (maxVal - minVal < 10) {
            float mid = (minVal + maxVal) / 2;
            minVal = mid - 5;
            maxVal = mid + 5;
        }
    } else if (isTemp && maxVal - minVal < 1) {
        float mid = (minVal + maxVal) / 2;
        minVal = mid - 0.5;
        maxVal = mid + 0.5;
    } else if (!isTemp && maxVal - minVal < 2) {
        float mid = (minVal + maxVal) / 2;
        minVal = mid - 1;
        maxVal = mid + 1;
    }

    float range = maxVal - minVal;
    if (range < 0.1) range = 1;

    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr);

    // Y 軸標籤 + 單位
    char buf[12];
    if (isRssi) {
        sprintf(buf, "%d dBm", (int)maxVal);
        u8g2.drawStr(0, chartY + 5, buf);
        sprintf(buf, "%d dBm", (int)minVal);
        u8g2.drawStr(0, chartY + chartH, buf);
    } else {
        sprintf(buf, isTemp ? "%.1f" : "%.0f", maxVal);
        u8g2.drawStr(0, chartY + 5, buf);
        sprintf(buf, isTemp ? "%.1f" : "%.0f", minVal);
        u8g2.drawStr(0, chartY + chartH, buf);
    }

    u8g2.drawFrame(chartX, chartY, chartW, chartH);

    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi :
                    isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum;
        int x = chartX + (i * chartW / displayCount);
        int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; lastY = y;
    }

    // 筆數
    char countStr[20];
    sprintf(countStr, "[%d/%d]", displayCount, historyCount);
    int countW = u8g2.getStrWidth(countStr);
    u8g2.drawStr(128 - countW - 2, 10, countStr);

    // Now / -X.Xh
    char offsetStr[10];
    if (historyViewOffset == 0) {
        strcpy(offsetStr, "Now");
    } else {
        float hours = (historyViewOffset * historyRecordInterval) / 3600000.0;
        sprintf(offsetStr, "-%.1fh", hours);
    }
    int offsetW = u8g2.getStrWidth(offsetStr);
    u8g2.drawStr(128 - offsetW - 2, chartY + chartH - 2, offsetStr);

    // VIEW 標籤
    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        u8g2.drawStr(100, 64, "VIEW");
    }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

// ==================== 其他畫面 ====================
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); }
    else { struct tm *ptm = localtime(&now); u8g2.setFont(u8g2_font_fub20_tn); char s[9]; sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec); u8g2.drawStr((128 - u8g2.getStrWidth(s))/2, 42, s); }
}

void drawDateScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); return; }
    struct tm *ptm = localtime(&now);
    const char* week[] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
    const char* month[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
    char day[4], year[6]; sprintf(day, "%02d", ptm->tm_mday); sprintf(year, "%d", ptm->tm_year + 1900);
    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, day);
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, week[ptm->tm_wday]);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(72, 40, month[ptm->tm_mon]); u8g2.drawStr(72, 56, year);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr(0, 12, city.c_str());
    if (WiFi.status() != WL_CONNECTED) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return; }
    if (weatherData.valid) {
        char buf[20]; const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); u8g2.drawStr(5, 50, icon);
        u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.1f", weatherData.temp); u8g2.drawStr(45, 32, buf);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buf) + 4, 15, 3);
        u8g2.setFont(u8g2_font_unifont_t_chinese2); u8g2.drawStr(45, 50, weatherData.description.c_str());
        u8g2.setFont(u8g2_font_ncenR10_tr); sprintf(buf, "H:%d%%", weatherData.humidity); u8g2.drawStr(45, 64, buf);
    } else { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "No data"); }
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
    if (WiFi.status() == WL_CONNECTED) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y += 12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y += 12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y += 12;
    } else { u8g2.drawStr(0, y, "WiFi Disconnected"); y += 12; }
    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap()/1024) + " KB").c_str()); y += 12;
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