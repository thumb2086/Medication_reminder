/*
  SmartMedBox Firmware v20.0
  硬體: ESP32-C6
  IDE: esp32 by Espressif Systems v3.0.0+
  板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

  v20.0 更新內容:
  - UI/UX 模式分離:
    - [新增] 普通模式: 預設模式，只顯示 4 個核心畫面 (時間/日期/天氣/感測器)，無次選單，介面極簡。
    - [新增] 工程模式: 需透過 App 開啟，啟用後會顯示所有 8 個畫面，並可進入系統選單執行進階操作。
  - 工程模式管理:
    - [移除] 裝置端切換: 移除所有在裝置上切換工程模式的方式。
    - [優化] App控制: 工程模式的開啟/關閉現在完全由 App (指令 0x13) 控制。
    - [新增] 狀態持久化: 工程模式的狀態會被儲存，裝置重啟後會維持在上次設定的模式。
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

// ==================== 腳位定義、Wi-Fi、BLE UUID & 指令碼、圖示 ====================
// (與 v19.0 相同，此處省略以保持簡潔)
#define I2C_SDA_PIN 22
#define I2C_SCL_PIN 21
#define ENCODER_A_PIN GPIO_NUM_18
#define ENCODER_B_PIN GPIO_NUM_19
#define ENCODER_PSH_PIN GPIO_NUM_23
#define BUTTON_CONFIRM_PIN 4
#define DHT_PIN 2
#define DHT_TYPE DHT11
const char* default_ssid = "charlie phone";
const char* default_password = "12345678";
String openWeatherMapApiKey = "ac1003d80943887d3d29d609afea98db";
String city = "Taipei";
String countryCode = "TW";
const float TEMP_CALIBRATION_OFFSET = 2.4;
const char* NTP_SERVER = "time.google.com";
const long GMT_OFFSET = 8 * 3600;
const int DAYLIGHT_OFFSET = 0;
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
const unsigned char icon_ble_bits[] U8X8_PROGMEM = {0x18, 0x24, 0x42, 0x5A, 0x5A, 0x42, 0x24, 0x18};
const unsigned char icon_sync_bits[] U8X8_PROGMEM = {0x00, 0x3C, 0x46, 0x91, 0x11, 0x26, 0x3C, 0x00};
const unsigned char icon_wifi_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x24, 0x42, 0x81, 0x42, 0x24, 0x18};
const unsigned char icon_wifi_fail_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x00};
const unsigned char icon_gear_bits[] U8X8_PROGMEM = {0x24, 0x18, 0x7E, 0x25, 0x52, 0x7E, 0x18, 0x24};

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;
File historyFile;

// ==================== 狀態與數據 ====================
enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_INFO_SCREEN };
UIMode currentUIMode = UI_MODE_MAIN_SCREENS;

enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_INFO, MENU_ITEM_REBOOT, MENU_ITEM_BACK, NUM_MENU_ITEMS };
SystemMenuItem selectedMenuItem = MENU_ITEM_WIFI;

enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;

enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4; // 預設為普通模式的畫面數量

ScreenState currentPageIndex = SCREEN_TIME;
static int lastViewOffset[3] = {0, 0, 0};
struct WeatherData { String description; float temp = 0; int humidity = 0; bool valid = false; } weatherData;
const int MAX_HISTORY = 4800;
const int HISTORY_WINDOW_SIZE = 60;
struct DataPoint { float temp; float hum; int16_t rssi; };
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
unsigned long syncIconStartTime = 0;
const unsigned long SYNC_ICON_DURATION = 3000;
unsigned long lastNTPResync = 0;
const unsigned long NTP_RESYNC_INTERVAL = 12 * 3600000;
unsigned long lastWeatherUpdate = 0;
const unsigned long WEATHER_INTERVAL = 600000;

// ==================== 函式宣告 ====================
// ... (此處省略與之前版本相同的宣告)
void drawSystemMenu();
void loadPersistentStates(); // v20.0 新增

// ==================== BLE 回呼 & 指令處理 ====================
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

void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t command = data[0];

    switch (command) {
        // (TIME_SYNC, WIFI_CREDENTIALS, STATUS, ENV, HISTORIC 指令與前版相同)
        case CMD_TIME_SYNC:
            if (length == 7) {
                tm timeinfo;
                timeinfo.tm_year = data[1] + 100; timeinfo.tm_mon  = data[2] - 1; timeinfo.tm_mday = data[3];
                timeinfo.tm_hour = data[4]; timeinfo.tm_min  = data[5]; timeinfo.tm_sec  = data[6];
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
                    connectWiFi(true);
                    sendTimeSyncAck();
                }
            }
            break;
        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                isEngineeringMode = (data[1] == 0x01);
                // v20.0: 持久化儲存工程模式狀態
                preferences.begin("medbox-meta", false);
                preferences.putBool("engMode", isEngineeringMode);
                preferences.end();

                // 立即更新 UI
                updateScreens();
                sendTimeSyncAck();
            }
            break;
        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC:
            if (!isSendingHistoricData) {
                isSendingHistoricData = true;
                historicDataIndexToSend = 0;
                historicDataStartTime = millis();
                Serial.println("Starting historic data transfer (batch mode)...");
            }
            break;
        default: sendErrorReport(0x03); break;
    }
}

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- SmartMedBox Firmware v20.0 ---");
    // ... (硬體初始化與前版相同)
    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin();
    u8g2.enableUTF8Print();
    dht.begin();
    if (!SPIFFS.begin(true)) { Serial.println("SPIFFS mount failed"); return; }

    initializeHistoryFile();
    loadHistoryMetadata();
    loadPersistentStates(); // v20.0: 讀取工程模式等持久化狀態

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("v20.0"))/2, 45, "v20.0");
    u8g2.sendBuffer();
    delay(2000);

    // ... (BLE 初始化與前版相同)
    BLEDevice::init("SmartMedBox");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic* pCommand = pServer->createCharacteristic(COMMAND_CHANNEL_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommand->setCallbacks(new CommandCallbacks());
    pDataEventCharacteristic = pServer->createCharacteristic(DATA_EVENT_CHANNEL_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pDataEventCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
    BLEDevice::getAdvertising()->setScanResponse(true);
    BLEDevice::startAdvertising();
    Serial.println("BLE Server started.");

    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    connectWiFi(true); // 開機只連線一次

    float t = dht.readTemperature(); float h = dht.readHumidity(); int16_t rssi = WiFi.RSSI();
    if (!isnan(t) && !isnan(h)) { addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi); }

    currentPageIndex = SCREEN_TIME;
    updateScreens(); // 根據讀取的工程模式狀態，初始化正確的畫面數量和旋鈕邊界
    rotaryEncoder.setEncoderValue(currentPageIndex);

    if (WiFi.status() == WL_CONNECTED) {
        if (MDNS.begin("smartmedbox")) { Serial.println("mDNS responder started"); }
        setupOTA();
    }
    Serial.println("--- Setup Complete ---\n");
}

// ==================== LOOP ====================
void loop() {
    if (isOtaMode) { ArduinoOTA.handle(); return; }

    handleHistoricDataTransfer();
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    // ... (定時任務與前版相同，已移除 Wi-Fi 自動重連)
    if (WiFi.status() == WL_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) { syncTimeNTPForce(); }
    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        float t = dht.readTemperature(); float h = dht.readHumidity(); int16_t rssi = WiFi.RSSI();
        if (!isnan(t) && !isnan(h)) { addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi); }
    }
    if (millis() - lastWeatherUpdate > WEATHER_INTERVAL && WiFi.status() == WL_CONNECTED) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }
    if (millis() - lastDisplayUpdate >= displayInterval) { updateDisplay(); }
}

// ==================== UI 核心函式 ====================
void updateDisplay() {
    lastDisplayUpdate = millis();
    u8g2.clearBuffer();

    switch (currentUIMode) {
        case UI_MODE_MAIN_SCREENS:
            switch (currentPageIndex) {
                case SCREEN_TIME: drawTimeScreen(); break;
                case SCREEN_DATE: drawDateScreen(); break;
                case SCREEN_WEATHER: drawWeatherScreen(); break;
                case SCREEN_SENSOR: drawSensorScreen(); break;
                    // 工程模式下才會顯示以下畫面
                case SCREEN_TEMP_CHART: if (isEngineeringMode) drawTempChartScreen(); break;
                case SCREEN_HUM_CHART: if (isEngineeringMode) drawHumChartScreen(); break;
                case SCREEN_RSSI_CHART: if (isEngineeringMode) drawRssiChartScreen(); break;
                case SCREEN_SYSTEM:
                    if (isEngineeringMode) {
                        u8g2.setFont(u8g2_font_ncenB10_tr);
                        u8g2.drawStr((128-u8g2.getStrWidth("System Menu"))/2, 38, "System Menu");
                    }
                    break;
            }
            break;
        case UI_MODE_SYSTEM_MENU:
            drawSystemMenu();
            break;
        case UI_MODE_INFO_SCREEN:
            drawSystemScreen();
            break;
    }

    drawStatusIcons();
    u8g2.sendBuffer();
}

void drawSystemMenu() {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128-u8g2.getStrWidth("System Menu"))/2, 12, "System Menu");

    // v20.0: 移除 "Toggle Eng. Mode" 選項
    const char* menuItems[] = {
            "Connect to WiFi",
            "System Info",
            "Reboot Device",
            "Back to Main"
    };

    for (int i = 0; i < NUM_MENU_ITEMS; i++) {
        int y = 26 + i * 12;
        if (i == selectedMenuItem) {
            u8g2.drawBox(0, y - 9, 128, 11);
            u8g2.setDrawColor(0);
            u8g2.drawStr(5, y, menuItems[i]);
            u8g2.setDrawColor(1);
        } else {
            u8g2.drawStr(5, y, menuItems[i]);
        }
    }
}

void drawStatusIcons() {
    if (currentUIMode != UI_MODE_MAIN_SCREENS || currentPageIndex != SCREEN_TIME) return;
    int x = 0; const int spacing = 10;
    if (bleDeviceConnected) { u8g2.drawXBM(x, 2, 8, 8, icon_ble_bits); x += spacing; }
    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) { u8g2.drawXBM(x, 2, 8, 8, icon_sync_bits); x += spacing; }
    if (WiFi.status() == WL_CONNECTED) { u8g2.drawXBM(x, 2, 8, 8, icon_wifi_bits); x += spacing; }
    else { u8g2.drawXBM(x, 2, 8, 8, icon_wifi_fail_bits); x += spacing; }
    if (isEngineeringMode) { u8g2.drawXBM(x, 2, 8, 8, icon_gear_bits); x += spacing; }
}

void updateScreens() {
    // 根據是否為工程模式，動態設定畫面數量
    NUM_SCREENS = isEngineeringMode ? 8 : 4;

    if (currentUIMode == UI_MODE_MAIN_SCREENS) {
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        if (currentPageIndex >= NUM_SCREENS) {
            currentPageIndex = SCREEN_TIME;
        }
        rotaryEncoder.setEncoderValue(currentPageIndex);
    } else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
        rotaryEncoder.setBoundaries(0, NUM_MENU_ITEMS - 1, true);
        rotaryEncoder.setEncoderValue(selectedMenuItem);
    }
    updateDisplay();
}

void handleEncoder() {
    if (rotaryEncoder.encoderChanged()) {
        if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            selectedMenuItem = (SystemMenuItem)rotaryEncoder.readEncoder();
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

        switch (currentUIMode) {
            case UI_MODE_MAIN_SCREENS:
                // 只有在工程模式下，且停在 SCREEN_SYSTEM 頁面時，才能進入選單
                if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) {
                    currentUIMode = UI_MODE_SYSTEM_MENU;
                    selectedMenuItem = MENU_ITEM_WIFI;
                    updateScreens();
                } else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) {
                    // ... (圖表頁的檢視模式切換邏輯與前版相同)
                    currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION;
                }
                break;

            case UI_MODE_SYSTEM_MENU:
                switch (selectedMenuItem) {
                    case MENU_ITEM_WIFI:
                        connectWiFi(true);
                        currentUIMode = UI_MODE_MAIN_SCREENS;
                        updateScreens();
                        break;
                    case MENU_ITEM_INFO:
                        currentUIMode = UI_MODE_INFO_SCREEN;
                        break;
                    case MENU_ITEM_REBOOT:
                        u8g2.clearBuffer();
                        u8g2.setFont(u8g2_font_ncenB10_tr);
                        u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2, 38, "Rebooting...");
                        u8g2.sendBuffer();
                        delay(1000);
                        ESP.restart();
                        break;
                    case MENU_ITEM_BACK:
                        currentUIMode = UI_MODE_MAIN_SCREENS;
                        currentPageIndex = SCREEN_TIME;
                        updateScreens();
                        break;
                }
                break;

            case UI_MODE_INFO_SCREEN:
                currentUIMode = UI_MODE_SYSTEM_MENU;
                break;
        }
        updateDisplay();
    }
}

void handleButtons() { // 返回鍵邏輯
    if (digitalRead(BUTTON_CONFIRM_PIN) == LOW && (millis() - lastConfirmPressTime > 300)) {
        lastConfirmPressTime = millis();

        switch (currentUIMode) {
            case UI_MODE_SYSTEM_MENU:
            case UI_MODE_INFO_SCREEN:
                currentUIMode = UI_MODE_MAIN_SCREENS;
                currentPageIndex = SCREEN_TIME;
                updateScreens();
                break;
            case UI_MODE_MAIN_SCREENS:
            default:
                currentPageIndex = SCREEN_TIME;
                rotaryEncoder.setEncoderValue(SCREEN_TIME);
                break;
        }
        updateDisplay();
    }
}

// ==================== 歷史資料與狀態處理 ====================
void loadPersistentStates() {
    preferences.begin("medbox-meta", true); // 以唯讀模式開啟
    isEngineeringMode = preferences.getBool("engMode", false); // 讀取工程模式，預設為 false
    preferences.end();
}

// ... (省略所有其他未變更的函式: BLE 指令處理細節、歷史資料、圖表、各畫面繪製等)
// 這些函式與 v19.0 版本基本相同