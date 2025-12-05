/*
SmartMedBox Firmware v21.2
硬體: ESP32-C6
IDE: esp32 by Espressif Systems v3.0.0+
板子: ESP32C6 Dev Module, 8MB with spiffs (3MB APP/1.5MB SPIFFS)

v21.2 更新內容 (Alarm & State Persistence):
[核心功能] 新增完整鬧鐘功能，可透過 BLE 設定，斷電後依然保存。
[核心功能] 鬧鐘觸發時燈光閃爍提醒，可按確認鍵停止。
[UX 優化] 新增斷電數據恢復功能，開機後立即顯示上次的溫濕度，無需等待。
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
#include <ESP32Servo.h>
#include <Adafruit_NeoPixel.h>

const char* FIRMWARE_VERSION = "v21.2";

// ==================== 腳位定義 (v20.6) ====================
#define I2C_SDA_PIN 22
#define I2C_SCL_PIN 21
#define ENCODER_A_PIN GPIO_NUM_19
#define ENCODER_B_PIN GPIO_NUM_18
#define ENCODER_PSH_PIN GPIO_NUM_20
#define BUTTON_CONFIRM_PIN 23
#define BUTTON_BACK_PIN 9
#define DHT_PIN 13
#define DHT_TYPE DHT11

// 新硬體腳位
#define BUZZER_PIN 10
#define BUZZER_PIN_2 11
#define SERVO_PIN 3
#define WS2812_PIN 15
#define NUM_LEDS 64

// ==================== Wi-Fi & NTP & OTA ====================
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
#define CMD_PROTOCOL_VERSION        0x01
#define CMD_TIME_SYNC               0x11
#define CMD_WIFI_CREDENTIALS        0x12
#define CMD_SET_ENGINEERING_MODE    0x13
#define CMD_REQUEST_ENG_MODE_STATUS 0x14
#define CMD_REQUEST_STATUS          0x20
#define CMD_REQUEST_ENV             0x30
#define CMD_REQUEST_HISTORIC        0x31
#define CMD_ENABLE_REALTIME         0x32
#define CMD_DISABLE_REALTIME        0x33
#define CMD_SET_ALARM               0x41
#define CMD_REPORT_PROTO_VER        0x71
#define CMD_REPORT_STATUS           0x80
#define CMD_REPORT_TAKEN            0x81
#define CMD_TIME_SYNC_ACK           0x82
#define CMD_REPORT_ENG_MODE_STATUS  0x83
#define CMD_REPORT_ENV              0x90
#define CMD_REPORT_HISTORIC_POINT   0x91
#define CMD_REPORT_HISTORIC_END     0x92
#define CMD_ERROR                   0xEE

// ... (圖示、全域物件等保持不變) ...
// ==================== 圖示 (XBM) ====================
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
BLECharacteristic *pDataEventCharacteristic = NULL;
Preferences preferences;
File historyFile;
Servo sg90;
Adafruit_NeoPixel pixels(NUM_LEDS, WS2812_PIN, NEO_GRB + NEO_KHZ800);

// ==================== 狀態與數據 ====================
enum WiFiState { WIFI_IDLE, WIFI_CONNECTING, WIFI_CONNECTED, WIFI_FAILED };
WiFiState wifiState = WIFI_IDLE;
unsigned long wifiConnectionStartTime = 0;
enum UIMode { UI_MODE_MAIN_SCREENS, UI_MODE_SYSTEM_MENU, UI_MODE_INFO_SCREEN };
UIMode currentUIMode = UI_MODE_MAIN_SCREENS;
enum SystemMenuItem { MENU_ITEM_WIFI, MENU_ITEM_OTA, MENU_ITEM_INFO, MENU_ITEM_REBOOT, MENU_ITEM_BACK, NUM_MENU_ITEMS };
SystemMenuItem selectedMenuItem = MENU_ITEM_WIFI;
int menuViewOffset = 0;
const int MAX_MENU_ITEMS_ON_SCREEN = 4;
enum EncoderMode { MODE_NAVIGATION, MODE_VIEW_ADJUST };
EncoderMode currentEncoderMode = MODE_NAVIGATION;
enum ScreenState { SCREEN_TIME, SCREEN_DATE, SCREEN_WEATHER, SCREEN_SENSOR, SCREEN_TEMP_CHART, SCREEN_HUM_CHART, SCREEN_RSSI_CHART, SCREEN_SYSTEM };
int NUM_SCREENS = 4;
ScreenState currentPageIndex = SCREEN_TIME;
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

// [v21.2 新增] 鬧鐘相關變數
uint8_t alarmHour = 0;
uint8_t alarmMinute = 0;
bool alarmEnabled = false;
bool isAlarmRinging = false;
unsigned long lastAlarmCheckTime = 0;


// ==================== 函式宣告 ====================
// ...
void checkAlarm(); // v21.2 新增

// ==================== BLE 回呼 & 指令處理 ====================
// ... (MyServerCallbacks, CommandCallbacks)
void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t command = data[0];
    Serial.printf("BLE RX: CMD=0x%02X, Len=%d\n", command, length);

    switch (command) {
        case CMD_PROTOCOL_VERSION:
            if (length == 1) {
                uint8_t packet[2] = {CMD_REPORT_PROTO_VER, 2};
                pDataEventCharacteristic->setValue(packet, 2);
                pDataEventCharacteristic->notify();
                Serial.println("Protocol Version 2 reported.");
            }
            break;

        case CMD_TIME_SYNC:
            if (length == 7) {
                tm timeinfo;
                timeinfo.tm_year = data[1] + 100; timeinfo.tm_mon  = data[2] - 1; timeinfo.tm_mday = data[3];
                timeinfo.tm_hour = data[4]; timeinfo.tm_min  = data[5]; timeinfo.tm_sec  = data[6];
                time_t t = mktime(&timeinfo); timeval tv = {t, 0};
                settimeofday(&tv, nullptr);
                syncIconStartTime = millis();
                sendTimeSyncAck();
            }
            break;
        case CMD_WIFI_CREDENTIALS:
            if (length >= 3) {
                uint8_t ssidLen = data[1]; uint8_t passLen = data[2 + ssidLen];
                if (length == 3 + ssidLen + passLen) {
                    String newSSID = String((char*)&data[2], ssidLen);
                    String newPASS = String((char*)&data[3 + ssidLen], passLen);
                    preferences.begin("wifi", false);
                    preferences.putString("ssid", newSSID); preferences.putString("pass", newPASS);
                    preferences.end();
                    startWiFiConnection();
                    sendTimeSyncAck();
                }
            }
            break;
        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                isEngineeringMode = (data[1] == 0x01);
                preferences.begin("medbox-meta", false);
                preferences.putBool("engMode", isEngineeringMode);
                preferences.end();
                updateScreens();
                sendTimeSyncAck();
            }
            break;
        case CMD_REQUEST_ENG_MODE_STATUS:
            if (length == 1) {
                uint8_t status = isEngineeringMode ? 0x01 : 0x00;
                uint8_t packet[2] = {CMD_REPORT_ENG_MODE_STATUS, status};
                pDataEventCharacteristic->setValue(packet, 2);
                pDataEventCharacteristic->notify();
            }
            break;

            // [v21.2 實作] 設定鬧鐘
        case CMD_SET_ALARM:
            // 預期格式: [0x41, Hour, Minute, Enabled] (長度至少 4)
            if (length >= 4) {
                uint8_t newHour = data[1];
                uint8_t newMinute = data[2];
                bool newEnabled = (data[3] != 0);

                // 簡單的資料驗證
                if (newHour < 24 && newMinute < 60) {
                    alarmHour = newHour;
                    alarmMinute = newMinute;
                    alarmEnabled = newEnabled;

                    // 儲存到 Flash (Preferences)
                    preferences.begin("medbox-meta", false);
                    preferences.putUChar("alarmH", alarmHour);
                    preferences.putUChar("alarmM", alarmMinute);
                    preferences.putBool("alarmOn", alarmEnabled);
                    preferences.end();

                    Serial.printf("Alarm Set: %02d:%02d, Enabled: %s\n", alarmHour, alarmMinute, alarmEnabled ? "ON" : "OFF");
                }
            }
            // 回傳 ACK
            sendTimeSyncAck();
            break;

        case CMD_REQUEST_STATUS: sendBoxStatus(); break;
        case CMD_REQUEST_ENV: sendSensorDataReport(); break;
        case CMD_REQUEST_HISTORIC:
            if (!isSendingHistoricData) {
                isSendingHistoricData = true; historicDataIndexToSend = 0; historicDataStartTime = millis();
                Serial.println("Starting historic data transfer (batch mode)...");
            }
            break;
        case CMD_ENABLE_REALTIME: isRealtimeEnabled = true; Serial.println("Real-time data enabled."); sendTimeSyncAck(); break;
        case CMD_DISABLE_REALTIME: isRealtimeEnabled = false; Serial.println("Real-time data disabled."); sendTimeSyncAck(); break;
        default:
            Serial.printf("Error: Unknown Command 0x%02X\n", command);
            sendErrorReport(0x03);
            break;
    }
}
//...
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.printf("\n--- SmartMedBox Firmware %s ---\n", FIRMWARE_VERSION);
    pinMode(ENCODER_PSH_PIN, INPUT_PULLUP);
    pinMode(BUTTON_CONFIRM_PIN, INPUT_PULLUP);
    pinMode(BUTTON_BACK_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    u8g2.begin();
    u8g2.enableUTF8Print();
    runPOST();
    dht.begin();
    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS mount failed");
        return;
    }
    initializeHistoryFile();
    loadHistoryMetadata();
    loadPersistentStates();
    rotaryEncoder.begin();
    rotaryEncoder.setup([] { rotaryEncoder.readEncoder_ISR(); }, [] {});
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB10_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("SmartMedBox"))/2, 30, "SmartMedBox");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth(FIRMWARE_VERSION))/2, 45, FIRMWARE_VERSION);
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
    updateSensorReadings();
    if (sensorDataValid) {
        addDataToHistory(cachedTemp, cachedHum, WiFi.RSSI());
    }
    currentPageIndex = SCREEN_TIME;
    updateScreens();
    rotaryEncoder.setEncoderValue(currentPageIndex);
    Serial.println("--- Setup Complete ---\n");
}

void loop() {
    if (isOtaMode) {
        ArduinoOTA.handle();
        if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 500)) {
            lastBackPressTime = millis();
            playConfirmSound();
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
    handleRealtimeData();
    updateSensorReadings();

    checkAlarm(); // [v21.2 新增] 檢查是否該響鈴

    // [v21.2 新增] 處理響鈴狀態
    if (isAlarmRinging) {
        // 讓燈閃爍
        if ((millis() / 200) % 2 == 0) {
            pixels.fill(pixels.Color(255, 0, 0)); // 紅燈
        } else {
            pixels.clear();
        }
        pixels.show();

        // 按下確認鍵停止鬧鐘
        if (digitalRead(BUTTON_CONFIRM_PIN) == LOW) {
            isAlarmRinging = false;
            pixels.clear();
            pixels.show();
            Serial.println("Alarm Stopped by user.");
            delay(500); // 防彈跳
        }
    }

    handleEncoder();
    handleEncoderPush();
    handleButtons();
    handleBackButton();
    if (wifiState == WIFI_CONNECTED && millis() - lastNTPResync >= NTP_RESYNC_INTERVAL) {
        syncTimeNTPForce();
    }
    if (millis() - lastHistoryRecord > historyRecordInterval) {
        lastHistoryRecord = millis();
        int16_t rssi = WiFi.RSSI();
        if (sensorDataValid) {
            addDataToHistory(cachedTemp, cachedHum, rssi);
        }
    }
    if (wifiState == WIFI_CONNECTED && millis() - lastWeatherUpdate > WEATHER_INTERVAL) {
        fetchWeatherData();
        lastWeatherUpdate = millis();
    }
    if (millis() - lastDisplayUpdate >= displayInterval) {
        updateDisplay();
    }
}

// ==================== 功能函式 ====================

void checkAlarm() {
    // 如果鬧鐘沒開，或正在響，就不檢查
    if (!alarmEnabled || isAlarmRinging) return;

    // 每秒檢查一次即可
    if (millis() - lastAlarmCheckTime < 1000) return;
    lastAlarmCheckTime = millis();

    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) return; // 尚未同步時間

    // 檢查 小時、分鐘 是否匹配，且秒數為 0 (避免一分鐘內重複觸發)
    if (timeinfo.tm_hour == alarmHour &&
        timeinfo.tm_min == alarmMinute &&
        timeinfo.tm_sec == 0) {

        Serial.println("ALARM TRIGGERED!");
        isAlarmRinging = true; // 進入響鈴模式

        // 觸發提醒音效
        playConfirmSound();
    }
}
//...
void addDataToHistory(float temp, float hum, int16_t rssi) {
    File file = SPIFFS.open("/history.dat", "r+");
    if (!file) return;

    DataPoint dp = {temp, hum, rssi};
    file.seek(historyIndex * sizeof(DataPoint));
    file.write((uint8_t*)&dp, sizeof(DataPoint));
    file.close();

    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;

    // [v21.2 修改] 儲存索引與最新的溫濕度快照到 NVS
    preferences.begin("medbox-meta", false);
    preferences.putInt("hist_count", historyCount);
    preferences.putInt("hist_index", historyIndex);

    // 新增：斷電儲存最後一筆溫濕度
    preferences.putFloat("last_temp", temp);
    preferences.putFloat("last_hum", hum);
    preferences.end();

    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
        rotaryEncoder.setBoundaries(0, maxOffset, false);
    }
}

void loadPersistentStates() {
    preferences.begin("medbox-meta", true);
    isEngineeringMode = preferences.getBool("engMode", false);

    // [v21.2 新增] 讀取鬧鐘設定
    alarmHour = preferences.getUChar("alarmH", 0);
    alarmMinute = preferences.getUChar("alarmM", 0);
    alarmEnabled = preferences.getBool("alarmOn", false);

    // [v21.2 新增] 讀取斷電前的最後溫濕度
    float savedTemp = preferences.getFloat("last_temp", 0.0);
    float savedHum = preferences.getFloat("last_hum", 0.0);

    // 如果有讀到有效數值 (非 0.0)，就先載入到快取
    if (savedTemp != 0.0 || savedHum != 0.0) {
        cachedTemp = savedTemp;
        cachedHum = savedHum;
        sensorDataValid = true; // 讓 UI 允許顯示
        Serial.printf("Restored last sensor data: T=%.1f, H=%.1f\n", cachedTemp, cachedHum);
    }

    preferences.end();
}
// ... (其餘所有函式與 v21.1 相同，此處省略)
void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    uint8_t packet[2] = {CMD_REPORT_STATUS, 0b00001111};
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
    if (!sensorDataValid) {
        sendErrorReport(0x02);
        return;
    }
    int16_t t_val = (int16_t)(cachedTemp * 100);
    int16_t h_val = (int16_t)(cachedHum * 100);
    uint8_t packet[5];
    packet[0] = CMD_REPORT_ENV;
    packet[1] = t_val & 0xFF;
    packet[2] = (t_val >> 8) & 0xFF;
    packet[3] = h_val & 0xFF;
    packet[4] = (h_val >> 8) & 0xFF;
    pDataEventCharacteristic->setValue(packet, 5);
    pDataEventCharacteristic->notify();
}
void sendRealtimeSensorData() {
    if (!bleDeviceConnected || !isRealtimeEnabled) return;
    if (!sensorDataValid) return;
    int16_t t_val = (int16_t)(cachedTemp * 100);
    int16_t h_val = (int16_t)(cachedHum * 100);
    uint8_t packet[5];
    packet[0] = CMD_REPORT_ENV;
    packet[1] = t_val & 0xFF;
    packet[2] = (t_val >> 8) & 0xFF;
    packet[3] = h_val & 0xFF;
    packet[4] = (h_val >> 8) & 0xFF;
    pDataEventCharacteristic->setValue(packet, 5);
    pDataEventCharacteristic->notify();
}
void sendHistoricDataEnd() {
    if (!bleDeviceConnected) return;
    uint8_t packet[1] = {CMD_REPORT_HISTORIC_END};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}
void handleHistoricDataTransfer() {
    if (!isSendingHistoricData) return;
    if (historicDataIndexToSend == 0) {
        historyFile = SPIFFS.open("/history.dat", "r");
        if (!historyFile) {
            sendErrorReport(0x04); isSendingHistoricData = false; return;
        }
    }
    if (!bleDeviceConnected) {
        historyFile.close(); isSendingHistoricData = false;
        Serial.println("BLE disconnected during transfer. Aborting."); return;
    }
    const int MAX_POINTS_PER_PACKET = 5;
    uint8_t batchPacket[2 + MAX_POINTS_PER_PACKET * 8];
    uint8_t pointsInBatch = 0;
    int packetWriteIndex = 2;
    while (pointsInBatch < MAX_POINTS_PER_PACKET && historicDataIndexToSend < historyCount) {
        DataPoint dp;
        int startIdx = (historyIndex - historyCount + MAX_HISTORY) % MAX_HISTORY;
        int currentReadIdx = (startIdx + historicDataIndexToSend) % MAX_HISTORY;
        historyFile.seek(currentReadIdx * sizeof(DataPoint));
        historyFile.read((uint8_t*)&dp, sizeof(DataPoint));
        time_t timestamp = time(nullptr) - (historyCount - 1 - historicDataIndexToSend) * (historyRecordInterval / 1000);
        batchPacket[packetWriteIndex++] = timestamp & 0xFF;
        batchPacket[packetWriteIndex++] = (timestamp >> 8) & 0xFF;
        batchPacket[packetWriteIndex++] = (timestamp >> 16) & 0xFF;
        batchPacket[packetWriteIndex++] = (timestamp >> 24) & 0xFF;
        int16_t t_val = (int16_t)(dp.temp * 100);
        int16_t h_val = (int16_t)(dp.hum * 100);
        batchPacket[packetWriteIndex++] = t_val & 0xFF;
        batchPacket[packetWriteIndex++] = (t_val >> 8) & 0xFF;
        batchPacket[packetWriteIndex++] = h_val & 0xFF;
        batchPacket[packetWriteIndex++] = (h_val >> 8) & 0xFF;
        pointsInBatch++;
        historicDataIndexToSend++;
    }
    if (pointsInBatch > 0) {
        batchPacket[0] = CMD_REPORT_HISTORIC_POINT;
        batchPacket[1] = pointsInBatch;
        pDataEventCharacteristic->setValue(batchPacket, 2 + pointsInBatch * 8);
        pDataEventCharacteristic->notify();
    }
    if (historicDataIndexToSend >= historyCount) {
        historyFile.close();
        sendHistoricDataEnd();
        isSendingHistoricData = false;
        unsigned long duration = millis() - historicDataStartTime;
        Serial.printf("Historic data transfer finished in %lu ms.\n", duration);
    }
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
void updateSensorReadings() {
    if (millis() - lastSensorReadTime >= SENSOR_READ_INTERVAL) {
        lastSensorReadTime = millis();
        float h = dht.readHumidity();
        float t = dht.readTemperature();
        if (!isnan(h) && !isnan(t)) {
            cachedHum = h;
            cachedTemp = t - TEMP_CALIBRATION_OFFSET;
            sensorDataValid = true;
        } else {
            Serial.println("Failed to read from DHT sensor!");
        }
    }
}
void runPOST() {
    sg90.attach(SERVO_PIN);
    pixels.begin();
    pixels.setBrightness(50);
    pixels.clear();
    pixels.show();
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(BUZZER_PIN, LOW);
    pinMode(BUZZER_PIN_2, OUTPUT);
    digitalWrite(BUZZER_PIN_2, LOW);
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("Hardware Check..."))/2, 38, "Hardware Check...");
    u8g2.sendBuffer();
    pixels.fill(pixels.Color(255, 0, 0)); pixels.show(); delay(300);
    pixels.fill(pixels.Color(0, 255, 0)); pixels.show(); delay(300);
    pixels.fill(pixels.Color(0, 0, 255)); pixels.show(); delay(300);
    pixels.clear(); pixels.show();
    tone(BUZZER_PIN, 1000, 100);
    tone(BUZZER_PIN_2, 1000, 100);
    delay(200);
    tone(BUZZER_PIN, 1500, 100);
    tone(BUZZER_PIN_2, 1500, 100);
    delay(200);
    sg90.write(0); delay(500);
    sg90.write(180); delay(500);
    sg90.write(0); delay(500);
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("Check OK"))/2, 38, "Check OK");
    u8g2.sendBuffer();
    delay(1000);
}
void playTickSound() {
    tone(BUZZER_PIN, 2000, 20);
    tone(BUZZER_PIN_2, 2000, 20);
}
void playConfirmSound() {
    tone(BUZZER_PIN, 1500, 50);
    tone(BUZZER_PIN_2, 1500, 50);
    delay(60);
    tone(BUZZER_PIN, 1000, 50);
    tone(BUZZER_PIN_2, 1000, 50);
}
void handleRealtimeData() {
    if (isRealtimeEnabled && bleDeviceConnected && (millis() - lastRealtimeSend > REALTIME_INTERVAL)) {
        lastRealtimeSend = millis();
        sendRealtimeSensorData();
    }
}
void returnToMainScreen() {
    currentUIMode = UI_MODE_MAIN_SCREENS;
    currentEncoderMode = MODE_NAVIGATION;
    currentPageIndex = SCREEN_TIME;
    updateScreens();
}
void handleBackButton() {
    if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 300)) {
        lastBackPressTime = millis();
        playConfirmSound();
        if (currentUIMode == UI_MODE_SYSTEM_MENU || currentUIMode == UI_MODE_INFO_SCREEN) {
            returnToMainScreen();
        } else if (currentUIMode == UI_MODE_MAIN_SCREENS && currentEncoderMode == MODE_VIEW_ADJUST) {
            currentEncoderMode = MODE_NAVIGATION;
        } else {
            currentPageIndex = SCREEN_TIME;
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
        }
        updateDisplay();
    }
}
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
        if (MDNS.begin("smartmedbox")) {
            Serial.println("mDNS responder started");
        }
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
            .onStart( [] { SPIFFS.end(); String type = (ArduinoOTA.getCommand() == U_FLASH) ? "sketch" : "filesystem"; drawOtaScreen("Updating " + type, 0); })
            .onProgress([](unsigned int progress, unsigned int total) { drawOtaScreen("Updating...", (progress / (total / 100))); })
            .onEnd( [] { drawOtaScreen("Complete!", 100); delay(1000); ESP.restart(); })
            .onError([](ota_error_t error) { String msg; if (error == OTA_AUTH_ERROR) msg = "Auth Failed"; else if (error == OTA_BEGIN_ERROR) msg = "Begin Failed"; else if (error == OTA_CONNECT_ERROR) msg = "Connect Failed"; else if (error == OTA_RECEIVE_ERROR) msg = "Receive Failed"; else if (error == OTA_END_ERROR) msg = "End Failed"; drawOtaScreen("Error: " + msg); delay(3000); ESP.restart(); });
    ArduinoOTA.begin();
    Serial.println("OTA service ready.");
}
void enterOtaMode() {
    if (isOtaMode || wifiState != WIFI_CONNECTED) {
        u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr((128 - u8g2.getStrWidth("Need WiFi for OTA")) / 2, 38, "Need WiFi for OTA"); u8g2.sendBuffer(); delay(2000);
        currentUIMode = UI_MODE_MAIN_SCREENS; updateScreens(); return;
    };
    isOtaMode = true; Serial.println("Entering OTA mode..."); lastBackPressTime = millis(); BLEDevice::deinit(true); String ip = WiFi.localIP().toString();
    u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(0, 12, "OTA Update Mode"); u8g2.setFont(u8g2_font_profont11_tf); u8g2.drawStr(0, 28, "smartmedbox.local");
    u8g2.drawStr(0, 42, ("IP: " + ip).c_str()); u8g2.drawStr(0, 56, "Press BACK to exit"); u8g2.sendBuffer();
}
void updateDisplay() {
    lastDisplayUpdate = millis(); u8g2.clearBuffer();
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
                case SCREEN_SYSTEM: if (isEngineeringMode) { u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr((128 - u8g2.getStrWidth("System Menu")) / 2, 38, "System Menu"); } break;
            }
            break;
        case UI_MODE_SYSTEM_MENU: drawSystemMenu(); break;
        case UI_MODE_INFO_SCREEN: drawSystemScreen(); break;
    }
    drawStatusIcons(); u8g2.sendBuffer();
}
void drawSystemMenu() {
    u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr((128 - u8g2.getStrWidth("System Menu")) / 2, 10, "System Menu");
    const char* menuItems[] = { "Connect to WiFi", "OTA Update", "System Info", "Reboot Device", "Back to Main" };
    for (int i = 0; i < MAX_MENU_ITEMS_ON_SCREEN; i++) {
        int itemIndex = menuViewOffset + i;
        if (itemIndex < NUM_MENU_ITEMS) {
            int y = 24 + i * 11;
            if (itemIndex == selectedMenuItem) { u8g2.drawBox(0, y - 9, 128 - 6, 11); u8g2.setDrawColor(0); u8g2.drawStr(5, y, menuItems[itemIndex]); u8g2.setDrawColor(1); }
            else { u8g2.drawStr(5, y, menuItems[itemIndex]); }
        }
    }
    if (NUM_MENU_ITEMS > MAX_MENU_ITEMS_ON_SCREEN) {
        int sbX = 122, sbY = 18, sbW = 4, sbH = 44; u8g2.drawFrame(sbX, sbY, sbW, sbH);
        int handleH = max(3, (int)((float)sbH * MAX_MENU_ITEMS_ON_SCREEN / NUM_MENU_ITEMS));
        int handleY = sbY + (int)((float)(sbH - handleH) * menuViewOffset / (NUM_MENU_ITEMS - MAX_MENU_ITEMS_ON_SCREEN));
        u8g2.drawBox(sbX, handleY, sbW, handleH);
    }
}
void drawOtaScreen(String text, int progress) {
    u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr((128 - u8g2.getStrWidth("OTA Update")) / 2, 12, "OTA Update");
    u8g2.setFont(u8g2_font_profont11_tf); u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 32, text.c_str());
    if (progress >= 0) { u8g2.drawFrame(14, 45, 100, 10); u8g2.drawBox(14, 45, progress, 10); }
    u8g2.sendBuffer();
}
void drawStatusIcons() {
    if (currentUIMode != UI_MODE_MAIN_SCREENS || currentPageIndex != SCREEN_TIME) return;
    int x = 0; const int spacing = 10;
    if (bleDeviceConnected) { u8g2.drawXBM(x, 2, 8, 8, icon_ble_bits); x += spacing; }
    if (millis() - syncIconStartTime < SYNC_ICON_DURATION && (millis() / 500) % 2 == 0) { u8g2.drawXBM(x, 2, 8, 8, icon_sync_bits); x += spacing; }
    switch (wifiState) {
        case WIFI_CONNECTED: u8g2.drawXBM(x, 2, 8, 8, icon_wifi_bits); break;
        case WIFI_CONNECTING: if ((millis() / 500) % 2 == 0) { u8g2.drawXBM(x, 2, 8, 8, icon_wifi_connecting_bits); } break;
        default: u8g2.drawXBM(x, 2, 8, 8, icon_wifi_fail_bits); break;
    }
    x += spacing;
    if (isEngineeringMode) { u8g2.drawXBM(x, 2, 8, 8, icon_gear_bits); x += spacing; }
}
void updateScreens() {
    NUM_SCREENS = isEngineeringMode ? 8 : 4;
    if (currentUIMode == UI_MODE_MAIN_SCREENS) {
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        if (currentPageIndex >= NUM_SCREENS) { currentPageIndex = SCREEN_TIME; }
        rotaryEncoder.setEncoderValue(currentPageIndex);
    } else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
        rotaryEncoder.setBoundaries(0, NUM_MENU_ITEMS - 1, true);
        rotaryEncoder.setEncoderValue(selectedMenuItem);
    }
    updateDisplay();
}
void handleEncoder() {
    if (rotaryEncoder.encoderChanged()) {
        playTickSound();
        if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            selectedMenuItem = (SystemMenuItem)rotaryEncoder.readEncoder();
            if (selectedMenuItem >= menuViewOffset + MAX_MENU_ITEMS_ON_SCREEN) { menuViewOffset = selectedMenuItem - MAX_MENU_ITEMS_ON_SCREEN + 1; }
            if (selectedMenuItem < menuViewOffset) { menuViewOffset = selectedMenuItem; }
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
        lastEncoderPushTime = millis(); playConfirmSound();
        switch (currentUIMode) {
            case UI_MODE_MAIN_SCREENS:
                if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) { currentUIMode = UI_MODE_SYSTEM_MENU; selectedMenuItem = MENU_ITEM_WIFI; menuViewOffset = 0; updateScreens(); }
                else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) { currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION; }
                break;
            case UI_MODE_SYSTEM_MENU:
                switch (selectedMenuItem) {
                    case MENU_ITEM_WIFI: u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr((128-u8g2.getStrWidth("Starting WiFi..."))/2,38,"Starting WiFi..."); u8g2.sendBuffer(); delay(1000); startWiFiConnection(); returnToMainScreen(); break;
                    case MENU_ITEM_OTA: enterOtaMode(); break;
                    case MENU_ITEM_INFO: currentUIMode = UI_MODE_INFO_SCREEN; break;
                    case MENU_ITEM_REBOOT: u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2,38,"Rebooting..."); u8g2.sendBuffer(); delay(1000); ESP.restart(); break;
                    case MENU_ITEM_BACK: returnToMainScreen(); break;
                }
                break;
            case UI_MODE_INFO_SCREEN: currentUIMode = UI_MODE_SYSTEM_MENU; break;
        }
        updateDisplay();
    }
}
void handleButtons() {
    bool isPressed = (digitalRead(BUTTON_CONFIRM_PIN) == LOW);
    if (isPressed && !confirmButtonPressed) { confirmPressStartTime = millis(); confirmButtonPressed = true; }
    else if (!isPressed && confirmButtonPressed) {
        if (millis() - confirmPressStartTime < 3000) { lastConfirmPressTime = millis(); playConfirmSound(); returnToMainScreen(); }
        confirmButtonPressed = false;
    }
    if (confirmButtonPressed && (millis() - confirmPressStartTime >= 3000)) { playConfirmSound(); enterOtaMode(); confirmButtonPressed = false; }
}
void loadHistoryMetadata() {
    preferences.begin("medbox-meta", true); historyCount = preferences.getInt("hist_count", 0); historyIndex = preferences.getInt("hist_index", 0); preferences.end();
}
void initializeHistoryFile() {
    if (!SPIFFS.exists("/history.dat")) {
        File file = SPIFFS.open("/history.dat", FILE_WRITE); if (!file) return;
        DataPoint empty = {0, 0, 0}; for (int i = 0; i < MAX_HISTORY; i++) { file.write((uint8_t*)&empty, sizeof(DataPoint)); } file.close();
    }
}
void loadHistoryWindow(int offset) {
    int points = min(historyCount, HISTORY_WINDOW_SIZE); if (points == 0) return;
    File file = SPIFFS.open("/history.dat", "r"); if (!file) return;
    int startIdx = (historyIndex - offset - points + MAX_HISTORY) % MAX_HISTORY;
    for (int i = 0; i < points; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint)); file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
}
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    u8g2.setFont(u8g2_font_6x10_tf); u8g2.drawStr(2, 8, title);
    if (historyCount < 2) { u8g2.setFont(u8g2_font_6x10_tf); u8g2.drawStr(10, 35, "No Data"); return; }
    loadHistoryWindow(historyViewOffset); int displayCount = min(HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) { u8g2.drawStr(10, 35, "Insufficient Data"); return; }
    float minVal = 999, maxVal = -999;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        if (val < minVal) minVal = val; if (val > maxVal) maxVal = val;
    }
    if (isRssi) {
        minVal = max(minVal, -100.0f); maxVal = min(maxVal, -30.0f);
        if (maxVal - minVal < 10) { float mid = (minVal + maxVal) / 2; minVal = mid - 5; maxVal = mid + 5; }
    } else if (isTemp && maxVal - minVal < 1) { float mid = (minVal + maxVal) / 2; minVal = mid - 0.5; maxVal = mid + 0.5; }
    else if (!isTemp && maxVal - minVal < 2) { float mid = (minVal + maxVal) / 2; minVal = mid - 1; maxVal = mid + 1; }
    float range = maxVal - minVal; if (range < 0.1) range = 1;
    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr); char buf[12];
    if (isRssi) { sprintf(buf, "%d", (int)maxVal); u8g2.drawStr(0, chartY + 5, buf); sprintf(buf, "%d", (int)minVal); u8g2.drawStr(0, chartY + chartH, buf); }
    else { sprintf(buf, isTemp ? "%.1f" : "%.0f", maxVal); u8g2.drawStr(0, chartY + 5, buf); sprintf(buf, isTemp ? "%.1f" : "%.0f", minVal); u8g2.drawStr(0, chartY + chartH, buf); }
    u8g2.drawFrame(chartX, chartY, chartW, chartH);
    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        int x = chartX + (i * chartW / displayCount); int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; lastY = y;
    }
    char countStr[20]; sprintf(countStr, "[%d/%d]", displayCount, historyCount); u8g2.drawStr(128 - u8g2.getStrWidth(countStr) - 2, 10, countStr);
    char offsetStr[10];
    if (historyViewOffset == 0) { strcpy(offsetStr, "Now"); } else { float hours = (historyViewOffset * historyRecordInterval) / 3600000.0; sprintf(offsetStr, "-%.1fh", hours); }
    u8g2.drawStr(128 - u8g2.getStrWidth(offsetStr) - 2, 64, offsetStr);
    if (currentEncoderMode == MODE_VIEW_ADJUST) { u8g2.drawStr(2, 64, "VIEW"); }
}
void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }
void drawTimeScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); }
    else { struct tm * ptm = localtime(&now); u8g2.setFont(u8g2_font_fub20_tn); char s[9]; sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec); u8g2.drawStr((128 - u8g2.getStrWidth(s)) / 2, 42, s); }
}
void drawDateScreen() {
    time_t now; time(&now);
    if (now < 1672531200) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 32, "Time not set"); return; }
    struct tm * ptm = localtime(&now); const char * week[] = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"}; const char * month[] = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    char day[4], year[6]; sprintf(day, "%02d", ptm->tm_mday); sprintf(year, "%d", ptm->tm_year + 1900);
    u8g2.setFont(u8g2_font_logisoso42_tn); u8g2.drawStr(5, 50, day); u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); u8g2.drawStr(72, 22, week[ptm->tm_wday]); u8g2.setFont(u8g2_font_ncenR10_tr);
    u8g2.drawStr(72, 40, month[ptm->tm_mon]); u8g2.drawStr(72, 56, year);
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
    if (!sensorDataValid) { u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10, 40, "Sensor Init..."); return; }
    char buf[20];
    u8g2.setFont(u8g2_font_helvB10_tr); u8g2.drawStr((128 - u8g2.getStrWidth("INDOOR")) / 2, 12, "INDOOR");
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 38, "TEMP");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.1f C", cachedTemp); u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 38, buf);
    u8g2.setFont(u8g2_font_ncenR10_tr); u8g2.drawStr(10, 62, "HUMI");
    u8g2.setFont(u8g2_font_fub25_tn); sprintf(buf, "%.0f %%", cachedHum); u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 62, buf);
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
    } else {
        weatherData.valid = false;
        Serial.printf("Weather fetch failed, error: %s\n", http.errorToString(httpCode).c_str());
    }
    http.end();
}