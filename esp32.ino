/*
  SmartMedBox Firmware v20.2
  硬體: ESP32-C6
  IDE: esp32 by Espressif Systems v3.0.0+
  板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

  v20.2 更新內容:
  - OTA 功能修復與增強:
    - [Bug修復] 修復了 v20.1 中返回鍵長按無法觸發 OTA 模式的問題。
    - [功能新增] 在工程模式的系統選單中，增加了 "OTA Update" 選項，提供第二個進入 OTA 模式的入口。
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
// (與 v20.1 相同，此處省略以保持簡潔)
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
const unsigned char icon_wifi_connecting_bits[] U8X8_PROGMEM = {0x00,0x00,0x0E,0x11,0x11,0x0E,0x00,0x00};

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ U8X8_PIN_NONE);
AiEsp32RotaryEncoder rotaryEncoder(ENCODER_A_PIN, ENCODER_B_PIN, ENCODER_PSH_PIN, -1, 4);
DHT dht(DHT_PIN, DHT_TYPE);
BLECharacteristic* pDataEventCharacteristic = NULL;
Preferences preferences;
File historyFile;

// ==================== 狀態與數據 ====================
enum WiFiState { WIFI_IDLE, WIFI_CONNECTING, WIFI_CONNECTED, WIFI_FAILED };
WiFiState wifiState = WIFI_IDLE;
unsigned long wifiConnectionStartTime = 0;

enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_INFO_SCREEN };
UIMode currentUIMode = UI_MODE_MAIN_SCREENS;

// v20.2: 新增 OTA 選項
enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_OTA, MENU_ITEM_INFO, MENU_ITEM_REBOOT, MENU_ITEM_BACK, NUM_MENU_ITEMS };
SystemMenuItem selectedMenuItem = MENU_ITEM_WIFI;

enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;
enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4;
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
unsigned long confirmPressStartTime = 0; // v20.2: 重新用於長按判斷
bool confirmButtonPressed = false;      // v20.2: 重新用於長按判斷
unsigned long syncIconStartTime = 0;
const unsigned long SYNC_ICON_DURATION = 3000;
unsigned long lastNTPResync = 0;
const unsigned long NTP_RESYNC_INTERVAL = 12 * 3600000;
unsigned long lastWeatherUpdate = 0;
const unsigned long WEATHER_INTERVAL = 600000;

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
void sendHistoricDataEnd();
void updateScreens();
void setupOTA();
void enterOtaMode();
void drawOtaScreen(String text, int progress = -1);
void handleHistoricDataTransfer();
void drawSystemMenu();
void loadPersistentStates();
void handleWiFiConnection();
void startWiFiConnection();

// ==================== BLE 回呼 & 指令處理 ====================
// (與 v20.1 相同，此處省略)
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
        case CMD_TIME_SYNC:
            if (length == 7) {
                tm timeinfo; timeinfo.tm_year = data[1] + 100; timeinfo.tm_mon  = data[2] - 1; timeinfo.tm_mday = data[3];
                timeinfo.tm_hour = data[4]; timeinfo.tm_min  = data[5]; timeinfo.tm_sec  = data[6];
                time_t t = mktime(&timeinfo); timeval tv = {t, 0}; settimeofday(&tv, nullptr);
                syncIconStartTime = millis(); sendTimeSyncAck();
            }
            break;
        case CMD_WIFI_CREDENTIALS:
            if (length >= 3) {
                uint8_t ssidLen = data[1]; uint8_t passLen = data[2 + ssidLen];
                if (length == 3 + ssidLen + passLen) {
                    String newSSID = String((char*)&data[2], ssidLen); String newPASS = String((char*)&data[3 + ssidLen], passLen);
                    preferences.begin("wifi", false); preferences.putString("ssid", newSSID); preferences.putString("pass", newPASS); preferences.end();
                    startWiFiConnection();
                    sendTimeSyncAck();
                }
            }
            break;
        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                isEngineeringMode = (data[1] == 0x01);
                preferences.begin("medbox-meta", false); preferences.putBool("engMode", isEngineeringMode); preferences.end();
                updateScreens(); sendTimeSyncAck();
            }
            break;
        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC:
            if (!isSendingHistoricData) {
                isSendingHistoricData = true; historicDataIndexToSend = 0; historicDataStartTime = millis();
                Serial.println("Starting historic data transfer (batch mode)...");
            }
            break;
        default: sendErrorReport(0x03); break;
    }
}
// (其他 BLE 相關函式 sendBoxStatus 等與前版相同，省略)
void sendBoxStatus() { if (!bleDeviceConnected) return; uint8_t p[2] = {CMD_REPORT_STATUS, 0b1111}; pDataEventCharacteristic->setValue(p, 2); pDataEventCharacteristic->notify(); }
void sendMedicationTaken(uint8_t s) { if (!bleDeviceConnected||s>7) return; uint8_t p[2]={CMD_REPORT_TAKEN,s}; pDataEventCharacteristic->setValue(p,2); pDataEventCharacteristic->notify(); }
void sendSensorDataReport() { if(!bleDeviceConnected)return; float t=dht.readTemperature()-TEMP_CALIBRATION_OFFSET; float h=dht.readHumidity(); if(isnan(h)||isnan(t)){sendErrorReport(2);return;} uint8_t p[5];p[0]=CMD_REPORT_ENV;p[1]=(uint8_t)t;p[2]=(uint8_t)((t-p[1])*100);p[3]=(uint8_t)h;p[4]=(uint8_t)((h-p[3])*100);pDataEventCharacteristic->setValue(p,5);pDataEventCharacteristic->notify();}
void sendHistoricDataEnd() { if (!bleDeviceConnected) return; uint8_t p[1] = {CMD_REPORT_HISTORIC_END}; pDataEventCharacteristic->setValue(p, 1); pDataEventCharacteristic->notify(); }
void handleHistoricDataTransfer() { if(!isSendingHistoricData)return; if(historicDataIndexToSend==0){historyFile=SPIFFS.open("/history.dat","r");if(!historyFile){sendErrorReport(4);isSendingHistoricData=false;return;}} if(!bleDeviceConnected){historyFile.close();isSendingHistoricData=false;return;} const int B=5; uint8_t p[2+B*8]; uint8_t n=0; int i=2; while(n<B&&historicDataIndexToSend<historyCount){DataPoint d; int s=(historyIndex-historyCount+MAX_HISTORY)%MAX_HISTORY; int c=(s+historicDataIndexToSend)%MAX_HISTORY; historyFile.seek(c*sizeof(DataPoint)); historyFile.read((uint8_t*)&d,sizeof(DataPoint)); time_t t=time(nullptr)-(historyCount-1-historicDataIndexToSend)*(historyRecordInterval/1000); p[i++]=t&0xFF;p[i++]=(t>>8)&0xFF;p[i++]=(t>>16)&0xFF;p[i++]=(t>>24)&0xFF;uint8_t ti=(uint8_t)d.temp;uint8_t tf=(uint8_t)((d.temp-ti)*100);uint8_t hi=(uint8_t)d.hum;uint8_t hf=(uint8_t)((d.hum-hi)*100);p[i++]=ti;p[i++]=tf;p[i++]=hi;p[i++]=hf;n++;historicDataIndexToSend++;} if(n>0){p[0]=CMD_REPORT_HISTORIC_POINT;p[1]=n;pDataEventCharacteristic->setValue(p,2+n*8);pDataEventCharacteristic->notify();} if(historicDataIndexToSend>=historyCount){historyFile.close();sendHistoricDataEnd();isSendingHistoricData=false;unsigned long u=millis()-historicDataStartTime;Serial.printf("Historic data transfer finished in %lu ms.\n",u);}}
void sendTimeSyncAck() { if (!bleDeviceConnected) return; uint8_t p[1] = {CMD_TIME_SYNC_ACK}; pDataEventCharacteristic->setValue(p, 1); pDataEventCharacteristic->notify(); }
void sendErrorReport(uint8_t e) { if (!bleDeviceConnected) return; uint8_t p[2] = {CMD_ERROR, e}; pDataEventCharacteristic->setValue(p, 2); pDataEventCharacteristic->notify(); }

// ==================== SETUP ====================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- SmartMedBox Firmware v20.2 ---");
    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin();
    u8g2.enableUTF8Print();
    dht.begin();
    if (!SPIFFS.begin(true)) { Serial.println("SPIFFS mount failed"); return; }

    initializeHistoryFile();
    loadHistoryMetadata();
    loadPersistentStates();

    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("v20.2"))/2, 45, "v20.2");
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
    startWiFiConnection();

    float t = dht.readTemperature(); float h = dht.readHumidity(); int16_t rssi = WiFi.RSSI();
    if (!isnan(t) && !isnan(h)) { addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi); }

    currentPageIndex = SCREEN_TIME;
    updateScreens();
    rotaryEncoder.setEncoderValue(currentPageIndex);

    Serial.println("--- Setup Complete ---\n");
}

// ==================== LOOP ====================
void loop() {
    if (isOtaMode) {
        ArduinoOTA.handle();
        if (digitalRead(BUTTON_CONFIRM_PIN) == LOW && (millis() - lastConfirmPressTime > 500)) {
            u8g2.clearBuffer();
            u8g2.setFont(u8g2_font_ncenB10_tr);
            u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2, 38, "Rebooting...");
            u8g2.sendBuffer();
            delay(1000);
            ESP.restart();
        }
        return;
    }

    handleWiFiConnection();
    handleHistoricDataTransfer();
    handleEncoder();
    handleEncoderPush();
    handleButtons();

    if (wifiState == WIFI_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) { syncTimeNTPForce(); }
    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        float t = dht.readTemperature(); float h = dht.readHumidity(); int16_t rssi = WiFi.RSSI();
        if (!isnan(t) && !isnan(h)) { addDataToHistory(t - TEMP_CALIBRATION_OFFSET, h, rssi); }
    }
    if (wifiState == WIFI_CONNECTED && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        fetchWeatherData(); lastWeatherUpdate = millis();
    }
    if (millis() - lastDisplayUpdate >= displayInterval) { updateDisplay(); }
}

// ==================== Wi-Fi, OTA, NTP ====================
void startWiFiConnection() {
    if (wifiState == WIFI_CONNECTING) return;
    wifiState = WIFI_CONNECTING;
    wifiConnectionStartTime = millis();
    preferences.begin("wifi", true);
    String savedSSID = preferences.getString("ssid", default_ssid);
    String savedPASS = preferences.getString("pass", default_password);
    preferences.end();
    WiFi.disconnect(true);
    delay(100);
    WiFi.begin(savedSSID.c_str(), savedPASS.c_str());
    Serial.println("Starting WiFi connection...");
}

void handleWiFiConnection() {
    if (wifiState != WIFI_CONNECTING) return;
    if (WiFi.status() == WL_CONNECTED) {
        wifiState = WIFI_CONNECTED;
        Serial.print("WiFi Connected! IP: ");
        Serial.println(WiFi.localIP());
        syncTimeNTPForce();
        fetchWeatherData();
        if (MDNS.begin("smartmedbox")) { Serial.println("mDNS responder started"); }
        setupOTA();
    } else if (millis() - wifiConnectionStartTime > 15000) {
        wifiState = WIFI_FAILED;
        WiFi.disconnect(true);
        Serial.println("WiFi Connection Failed (Timeout).");
    }
}

void syncTimeNTPForce() {
    if (wifiState != WIFI_CONNECTED) return;
    configTime(GMT_OFFSET, DAYLIGHT_OFFSET, NTP_SERVER);
    struct tm timeinfo;
    if (getLocalTime(&timeinfo, 5000)) {
        syncIconStartTime = millis();
        lastNTPResync = millis();
        Serial.println("NTP Time synced.");
    } else {
        Serial.println("NTP Time sync failed.");
    }
}

void setupOTA() {
    ArduinoOTA.setHostname("smartmedbox");
    ArduinoOTA.setPassword("medbox123");
    ArduinoOTA
            .onStart([]() { SPIFFS.end(); drawOtaScreen("Updating " + String((ArduinoOTA.getCommand()==U_FLASH)?"sketch":"fs"), 0); })
            .onProgress([](unsigned int p, unsigned int t) { drawOtaScreen("Updating...", (p/(t/100))); })
            .onEnd([]() { drawOtaScreen("Complete!", 100); delay(1000); ESP.restart(); })
            .onError([](ota_error_t e) {
                String m;
                if(e==OTA_AUTH_ERROR)m="Auth Fail"; else if(e==OTA_BEGIN_ERROR)m="Begin Fail";
                else if(e==OTA_CONNECT_ERROR)m="Connect Fail"; else if(e==OTA_RECEIVE_ERROR)m="Receive Fail";
                else if(e==OTA_END_ERROR)m="End Fail";
                drawOtaScreen("Error: "+m); delay(3000); ESP.restart();
            });
    ArduinoOTA.begin();
    Serial.println("OTA service ready.");
}

void enterOtaMode() {
    if (isOtaMode || wifiState != WIFI_CONNECTED) {
        u8g2.clearBuffer();
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr((128-u8g2.getStrWidth("Need WiFi for OTA"))/2, 38, "Need WiFi for OTA");
        u8g2.sendBuffer();
        delay(2000);
        currentUIMode = UI_MODE_MAIN_SCREENS; // 返回主畫面
        updateScreens();
        return;
    };
    isOtaMode = true;
    Serial.println("Entering OTA mode...");
    lastConfirmPressTime = millis();
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
    const char* menuItems[] = { "Connect to WiFi", "OTA Update", "System Info", "Reboot Device", "Back to Main" };
    for (int i = 0; i < NUM_MENU_ITEMS; i++) {
        int y = 26 + i * 11;
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

void drawOtaScreen(String text, int progress) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("OTA Update"))/2, 12, "OTA Update");
    u8g2.setFont(u8g2_font_profont11_tf);
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str()))/2, 32, text.c_str());
    if (progress >= 0) {
        u8g2.drawFrame(14, 45, 100, 10);
        u8g2.drawBox(14, 45, progress, 10);
    }
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentUIMode != UI_MODE_MAIN_SCREENS || currentPageIndex != SCREEN_TIME) return;
    int x = 0; const int spacing = 10;
    if (bleDeviceConnected) { u8g2.drawXBM(x, 2, 8, 8, icon_ble_bits); x += spacing; }
    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) { u8g2.drawXBM(x, 2, 8, 8, icon_sync_bits); x += spacing; }
    switch(wifiState) {
        case WIFI_CONNECTED: u8g2.drawXBM(x, 2, 8, 8, icon_wifi_bits); break;
        case WIFI_CONNECTING: if ((millis()/500)%2==0) { u8g2.drawXBM(x, 2, 8, 8, icon_wifi_connecting_bits); } break;
        default: u8g2.drawXBM(x, 2, 8, 8, icon_wifi_fail_bits); break;
    }
    x += spacing;
    if (isEngineeringMode) { u8g2.drawXBM(x, 2, 8, 8, icon_gear_bits); x += spacing; }
}

void updateScreens() {
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

// ==================== 按鍵處理 (v20.2) ====================
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
                if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) {
                    currentUIMode = UI_MODE_SYSTEM_MENU;
                    selectedMenuItem = MENU_ITEM_WIFI;
                    updateScreens();
                } else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) {
                    currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION;
                }
                break;
            case UI_MODE_SYSTEM_MENU:
                switch (selectedMenuItem) {
                    case MENU_ITEM_WIFI:
                        u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr((128-u8g2.getStrWidth("Starting WiFi..."))/2,38,"Starting WiFi..."); u8g2.sendBuffer(); delay(1000);
                        startWiFiConnection();
                        currentUIMode = UI_MODE_MAIN_SCREENS; currentPageIndex = SCREEN_TIME; updateScreens();
                        break;
                    case MENU_ITEM_OTA: // v20.2: 新增
                        enterOtaMode();
                        break;
                    case MENU_ITEM_INFO:
                        currentUIMode = UI_MODE_INFO_SCREEN;
                        break;
                    case MENU_ITEM_REBOOT:
                        u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2,38,"Rebooting..."); u8g2.sendBuffer(); delay(1000);
                        ESP.restart();
                        break;
                    case MENU_ITEM_BACK:
                        currentUIMode = UI_MODE_MAIN_SCREENS; currentPageIndex = SCREEN_TIME; updateScreens();
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

void handleButtons() {
    bool isPressed = (digitalRead(BUTTON_CONFIRM_PIN) == LOW);
    if (isPressed && !confirmButtonPressed) { // 按下事件
        confirmPressStartTime = millis();
        confirmButtonPressed = true;
    } else if (!isPressed && confirmButtonPressed) { // 釋放事件
        if (millis() - confirmPressStartTime < 3000) { // 短按
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
        confirmButtonPressed = false; // 重置狀態
    }

    // 按住期間的長按檢查
    if (confirmButtonPressed && (millis() - confirmPressStartTime >= 3000)) {
        enterOtaMode();
        confirmButtonPressed = false; // 觸發後重置，避免重複觸發
    }
}


// ==================== 歷史資料與狀態處理 ====================
void loadPersistentStates() {
    preferences.begin("medbox-meta", true);
    isEngineeringMode = preferences.getBool("engMode", false);
    preferences.end();
}

void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE);
        if (!file) return;
        DataPoint empty = {0, 0, 0};
        for (int i = 0; i < MAX_HISTORY; i++) { file.write((uint8_t*)&empty, sizeof(DataPoint)); }
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

// ==================== 圖表與畫面繪製 ====================
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(2, 8, title);
    if (historyCount < 2) { u8g2.setFont(u8g2_font_6x10_tf); u8g2.drawStr(10, 35, "No Data"); return; }
    loadHistoryWindow(historyViewOffset);
    int displayCount = min(HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) { u8g2.drawStr(10, 35, "Insufficient Data"); return; }
    float minVal = 999, maxVal = -999;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        if (val < minVal) minVal = val;
        if (val > maxVal) maxVal = val;
    }
    if (isRssi) {
        minVal = max(minVal, -100.0f); maxVal = min(maxVal, -30.0f);
        if (maxVal-minVal<10) { float mid=(minVal+maxVal)/2; minVal=mid-5; maxVal=mid+5; }
    } else if (isTemp && maxVal-minVal<1) { float mid=(minVal+maxVal)/2; minVal=mid-0.5; maxVal=mid+0.5;
    } else if (!isTemp && maxVal-minVal<2) { float mid=(minVal+maxVal)/2; minVal=mid-1; maxVal=mid+1; }
    float range = maxVal - minVal;
    if (range < 0.1) range = 1;
    int chartX=18, chartY=15, chartW=128-chartX-2, chartH=40;
    u8g2.setFont(u8g2_font_5x7_tr);
    char buf[12];
    if (isRssi) { sprintf(buf, "%d", (int)maxVal); u8g2.drawStr(0, chartY+5, buf); sprintf(buf, "%d", (int)minVal); u8g2.drawStr(0, chartY+chartH, buf); }
    else { sprintf(buf, isTemp?"%.1f":"%.0f", maxVal); u8g2.drawStr(0, chartY+5, buf); sprintf(buf, isTemp?"%.1f":"%.0f", minVal); u8g2.drawStr(0, chartY+chartH, buf); }
    u8g2.drawFrame(chartX, chartY, chartW, chartH);
    int lastX=-1, lastY=-1;
    for (int i=0; i<displayCount; i++) {
        float val = isRssi?historyWindowBuffer[i].rssi:(isTemp?historyWindowBuffer[i].temp:historyWindowBuffer[i].hum);
        int x=chartX+(i*chartW/displayCount);
        int y=chartY+chartH-1-((val-minVal)/range*(chartH-2));
        if (lastX>=0) u8g2.drawLine(lastX, lastY, x, y);
        lastX=x; lastY=y;
    }
    char countStr[20]; sprintf(countStr, "[%d/%d]", displayCount, historyCount);
    u8g2.drawStr(128 - u8g2.getStrWidth(countStr) - 2, 10, countStr);
    char offsetStr[10];
    if (historyViewOffset==0) { strcpy(offsetStr, "Now"); }
    else { float hours=(historyViewOffset*historyRecordInterval)/3600000.0; sprintf(offsetStr, "-%.1fh", hours); }
    u8g2.drawStr(128 - u8g2.getStrWidth(offsetStr) - 2, 64, offsetStr);
    if (currentEncoderMode==MODE_VIEW_ADJUST) { u8g2.drawStr(2, 64, "VIEW"); }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

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
    if (wifiState != WIFI_CONNECTED) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 40, "No WiFi"); return; }
    if (weatherData.valid) {
        char buf[20]; const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); u8g2.drawStr(5, 50, icon);
        u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.1f", weatherData.temp); u8g2.drawStr(45, 32, buf);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buf) + 4, 15, 3);
        u8g2.setFont(u8g2_font_unifont_t_chinese2); u8g2.drawStr(45, 50, weatherData.description.c_str());
        u8g2.setFont(u8g2_font_ncenR10_tr); sprintf(buf, "H:%d%%", weatherData.humidity); u8g2.drawStr(45, 64, buf);
    } else { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 32, "Updating..."); }
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
    if (wifiState != WIFI_CONNECTED) { weatherData.valid = false; return; }
    HTTPClient http;
    String url = "http://api.openweathermap.org/data/2.5/weather?q=" + city + "," + countryCode + "&units=metric&lang=zh_tw&APPID=" + openWeatherMapApiKey;
    http.begin(url);
    int httpCode = http.GET();
    if (httpCode > 0) {
        String payload = http.getString();
        int d1 = payload.indexOf("\"description\":\"") + 15; int d2 = payload.indexOf("\"", d1);
        if (d1 > 14 && d2 > d1) weatherData.description = payload.substring(d1, d2);
        int t1 = payload.indexOf("\"temp\":") + 7; int t2 = payload.indexOf(",", t1);
        if (t1 > 6 && t2 > t1) weatherData.temp = payload.substring(t1, t2).toFloat();
        int h1 = payload.indexOf("\"humidity\":") + 11; int h2 = payload.indexOf("}", h1);
        if (h1 > 10 && h2 > h1) weatherData.humidity = payload.substring(h1, h2).toInt();
        weatherData.valid = true;
    } else {
        weatherData.valid = false;
        Serial.printf("Weather fetch failed, error: %s\n", http.errorToString(httpCode).c_str());
    }
    http.end();
}