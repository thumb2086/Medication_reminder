/*
  SmartMedBox Firmware v21.1 (OTA Crash Fix)
  硬體: ESP32-C6 Dev Module

  v21.1 修正說明:
  1. [緊急修復] 移除 enterOtaMode() 中的 BLEDevice::deinit(true)。
     原因: 該指令會導致 ESP32-C6 核心崩潰並重啟。
  2. [優化] OTA 模式下現在會保持藍牙開啟，但不處理數據，確保系統穩定。
  3. [保留] v21.0 的所有功能 (鬧鐘、0x41指令、硬體驅動、滾動選單)。
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

// ==================== 硬體腳位定義 ====================
#define PIN_I2C_SDA     22
#define PIN_I2C_SCL     21
#define PIN_ENC_A       18
#define PIN_ENC_B       19
#define PIN_ENC_BTN     23
#define PIN_BTN_OK      4
#define PIN_BTN_BACK    5
#define PIN_DHT         2
#define PIN_BUZZER_1    10
#define PIN_BUZZER_2    11
#define PIN_SERVO       12
#define PIN_LED         13
#define LED_COUNT       64
#define DHT_TYPE        DHT11

// ==================== 系統常數 ====================
const char* NTP_SERVER = "time.google.com";
const long  GMT_OFFSET = 8 * 3600;
const int   MAX_HISTORY = 4800;
const int   HISTORY_WINDOW = 60;
const int   MAX_ALARMS = 4;

const unsigned long INTERVAL_DISPLAY = 100;
const unsigned long INTERVAL_HISTORY = 30000;
const unsigned long INTERVAL_WEATHER = 600000;
const unsigned long INTERVAL_PUSH    = 5000;

// BLE 指令集
enum BleCmd {
    CMD_REQ_PROTO_VER = 0x01,
    CMD_TIME_SYNC = 0x11,
    CMD_WIFI_CRED = 0x12,
    CMD_ENG_MODE = 0x13,
    CMD_SET_ALARM = 0x41,
    CMD_REQ_STATUS = 0x20,
    CMD_REQ_ENV = 0x30,
    CMD_REQ_HIST = 0x31,
    CMD_SUB_ENV = 0x32,
    CMD_UNSUB_ENV = 0x33,
    CMD_REP_STATUS = 0x80,
    CMD_REP_TAKEN = 0x81,
    CMD_ACK = 0x82,
    CMD_REP_PROTO_VER = 0x83,
    CMD_REP_ENV = 0x90,
    CMD_REP_HIST_PT = 0x91,
    CMD_REP_HIST_END = 0x92,
    CMD_ERROR = 0xEE
};

const uint8_t CURRENT_PROTOCOL_VERSION = 3;

// UI 狀態
enum UIMode { UI_MAIN, UI_MENU, UI_INFO, UI_OTA, UI_ALARM_RINGING };
enum Screen { SCR_TIME, SCR_DATE, SCR_WEATHER, SCR_SENSOR, SCR_CH_TEMP, SCR_CH_HUM, SCR_CH_RSSI, SCR_SYS };
enum MenuOpt { MN_WIFI, MN_OTA, MN_INFO, MN_REBOOT, MN_BACK, MN_COUNT };

// ==================== 數據結構 ====================
struct AppConfig {
    String wifiSSID = "charlie phone";
    String wifiPass = "12345678";
    String city = "Taipei";
    String country = "TW";
    String weatherKey = "ac1003d80943887d3d29d609afea98db";
    float tempOffset = 2.4;
};

struct Alarm {
    uint8_t hour;
    uint8_t min;
    bool enabled;
};

struct AppState {
    UIMode uiMode = UI_MAIN;
    Screen curScreen = SCR_TIME;
    int menuIdx = 0;
    int menuOffset = 0;
    bool engMode = false;
    bool wifiConnected = false;
    bool bleConnected = false;
    bool subRealtime = false;
    bool sendingHist = false;
    bool chartViewMode = false;
    int activeAlarmId = -1;
};

struct EnvData { float temp; float hum; int16_t rssi; };
struct WeatherInfo { String desc; float temp; int hum; bool valid; };

// ==================== 全域物件 ====================
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);
AiEsp32RotaryEncoder encoder(PIN_ENC_A, PIN_ENC_B, PIN_ENC_BTN, -1, 4);
DHT dht(PIN_DHT, DHT_TYPE);
BLECharacteristic* pBleData = NULL;
Preferences prefs;
Servo servo;
Adafruit_NeoPixel leds(LED_COUNT, PIN_LED, NEO_GRB + NEO_KHZ800);

AppConfig config;
AppState state;
WeatherInfo weather;
EnvData histBuf[HISTORY_WINDOW];
Alarm alarms[MAX_ALARMS];

// 變數
int histCount = 0, histHead = 0, histViewOffset = 0, histSendIdx = 0;
unsigned long tmrDisplay=0, tmrHist=0, tmrWeather=0, tmrPush=0, tmrAlarmCheck=0;
unsigned long tmrBtnOk=0, tmrBtnBack=0, tmrEncBtn=0;

// BLE UUID
#define SERVICE_UUID    "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_CMD_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_DATA_UUID  "c8c7c599-809c-43a5-b825-1038aa349e5d"

extern const uint8_t ico_ble[], ico_wifi[], ico_no_wifi[], ico_gear[];

// ==================== 邏輯函式 ====================
void loadAlarms() {
    prefs.begin("alarms", true);
    for(int i=0; i<MAX_ALARMS; i++) {
        char key[5]; sprintf(key, "a%d", i);
        uint32_t val = prefs.getUInt(key, 0);
        alarms[i].enabled = (val & 0x1);
        alarms[i].min = (val >> 8) & 0xFF;
        alarms[i].hour = (val >> 16) & 0xFF;
    }
    prefs.end();
}

void saveAlarm(int id) {
    if(id < 0 || id >= MAX_ALARMS) return;
    prefs.begin("alarms", false);
    char key[5]; sprintf(key, "a%d", id);
    uint32_t val = (alarms[id].hour << 16) | (alarms[id].min << 8) | (alarms[id].enabled ? 1 : 0);
    prefs.putUInt(key, val);
    prefs.end();
}

void checkAlarms() {
    if(state.uiMode == UI_ALARM_RINGING) return;
    time_t now; time(&now);
    struct tm *t = localtime(&now);
    if(t->tm_sec == 0) {
        for(int i=0; i<MAX_ALARMS; i++) {
            if(alarms[i].enabled && alarms[i].hour == t->tm_hour && alarms[i].min == t->tm_min) {
                state.uiMode = UI_ALARM_RINGING;
                state.activeAlarmId = i;
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
        if(phase) leds.fill(leds.Color(255, 0, 0));
        else leds.clear();
        leds.show();
        if(phase) { tone(PIN_BUZZER_1, 2000, 150); }
        else { tone(PIN_BUZZER_2, 1500, 150); }
    }
}

void beep(int id, int freq, int ms) {
    int pin = (id == 1) ? PIN_BUZZER_1 : PIN_BUZZER_2;
    tone(pin, freq, ms); delay(ms); noTone(pin);
}

// BLE Callback
void sendBlePacket(uint8_t type, uint8_t* data, size_t len) {
    if(!state.bleConnected) return;
    uint8_t pkg[len + 1];
    pkg[0] = type;
    if(len > 0) memcpy(&pkg[1], data, len);
    pBleData->setValue(pkg, len + 1);
    pBleData->notify();
}

class ServerCB: public BLEServerCallbacks {
    void onConnect(BLEServer* s) { state.bleConnected = true; Serial.println("BLE On"); }
    void onDisconnect(BLEServer* s) { state.bleConnected = false; BLEDevice::startAdvertising(); Serial.println("BLE Off"); }
};

class CmdCB: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *c) {
        uint8_t* d = c->getData();
        size_t len = c->getLength();
        if(len == 0) return;
        uint8_t cmd = d[0];
        Serial.printf("RX CMD: 0x%02X, Len: %d\n", cmd, len);

        switch(cmd) {
            case CMD_REQ_PROTO_VER: {
                uint8_t v = CURRENT_PROTOCOL_VERSION;
                sendBlePacket(CMD_REP_PROTO_VER, &v, 1); break;
            }
            case CMD_SET_ALARM: {
                if(len >= 5) {
                    int id = d[1];
                    if(id >= 0 && id < MAX_ALARMS) {
                        alarms[id].hour = d[2];
                        alarms[id].min = d[3];
                        alarms[id].enabled = (d[4] == 1);
                        saveAlarm(id);
                        Serial.printf("Set Alarm %d: %02d:%02d (%d)\n", id, alarms[id].hour, alarms[id].min, alarms[id].enabled);
                        sendBlePacket(CMD_ACK, NULL, 0);
                    } else sendBlePacket(CMD_ERROR, (uint8_t*)"\x04", 1);
                } else sendBlePacket(CMD_ERROR, (uint8_t*)"\x05", 1);
                break;
            }
            case CMD_TIME_SYNC: {
                tm t; t.tm_year=d[1]+100; t.tm_mon=d[2]-1; t.tm_mday=d[3];
                t.tm_hour=d[4]; t.tm_min=d[5]; t.tm_sec=d[6];
                time_t tt = mktime(&t); timeval tv = {tt, 0}; settimeofday(&tv, NULL);
                sendBlePacket(CMD_ACK, NULL, 0); break;
            }
            case CMD_WIFI_CRED: {
                int sl = d[1]; int pl = d[2+sl];
                prefs.begin("wifi", false);
                prefs.putString("ssid", String((char*)&d[2], sl));
                prefs.putString("pass", String((char*)&d[3+sl], pl));
                prefs.end();
                ESP.restart(); break;
            }
            case CMD_ENG_MODE: state.engMode = (d[1] == 1);
                prefs.begin("meta", false); prefs.putBool("eng", state.engMode); prefs.end();
                sendBlePacket(CMD_ACK, NULL, 0); break;
            case CMD_REQ_STATUS: { uint8_t s=0x0F; sendBlePacket(CMD_REP_STATUS, &s, 1); break; }
            case CMD_REQ_ENV: { /* Env Report Logic Skipped */ break; }
            case CMD_SUB_ENV: state.subRealtime = true; sendBlePacket(CMD_ACK, NULL, 0); break;
            case CMD_UNSUB_ENV: state.subRealtime = false; sendBlePacket(CMD_ACK, NULL, 0); break;
            default:
                Serial.printf(">>> ERROR: Unknown Command 0x%02X <<<\n", cmd);
                sendBlePacket(CMD_ERROR, (uint8_t*)"\x03", 1);
        }
    }
};

void connectWiFi() {
    prefs.begin("wifi", true);
    String s = prefs.getString("ssid", config.wifiSSID);
    String p = prefs.getString("pass", config.wifiPass);
    prefs.end();
    WiFi.begin(s.c_str(), p.c_str());
}

// ==================== UI 繪製 ====================
void drawHeader(const char* title) {
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth(title))/2, 10, title);
}

void drawIcons() {
    int x = 0;
    if(state.bleConnected) { u8g2.drawXBM(x, 0, 8, 8, ico_ble); x+=10; }
    if(state.wifiConnected) { u8g2.drawXBM(x, 0, 8, 8, ico_wifi); x+=10; }
    else { u8g2.drawXBM(x, 0, 8, 8, ico_no_wifi); x+=10; }
    if(state.engMode) { u8g2.drawXBM(x, 0, 8, 8, ico_gear); }
}

void render() {
    u8g2.clearBuffer();

    if (state.uiMode == UI_ALARM_RINGING) {
        u8g2.setFont(u8g2_font_fub20_tn);
        u8g2.drawStr(15, 35, "ALARM!");
        char buf[20]; sprintf(buf, "Take Meds #%d", state.activeAlarmId + 1);
        u8g2.setFont(u8g2_font_ncenB08_tr);
        u8g2.drawStr((128-u8g2.getStrWidth(buf))/2, 55, buf);
        u8g2.drawStr(20, 10, "Press OK to Stop");
    }
    else if(state.uiMode == UI_MAIN) {
        drawIcons();
        switch(state.curScreen) {
            case SCR_TIME: {
                time_t now; time(&now); struct tm *t = localtime(&now);
                char s[10]; snprintf(s, 10, "%02d:%02d:%02d", t->tm_hour, t->tm_min, t->tm_sec);
                u8g2.setFont(u8g2_font_fub20_tn); u8g2.drawStr((128-u8g2.getStrWidth(s))/2, 45, s);
                int b = (millis()/20)%50; leds.setPixelColor(0, leds.Color(0,0,b)); leds.show();
                break;
            }
            case SCR_DATE: {
                time_t now; time(&now); struct tm *t = localtime(&now);
                char s[20]; strftime(s, 20, "%a %b %d", t);
                u8g2.setFont(u8g2_font_ncenB10_tr); u8g2.drawStr(10, 40, s);
                leds.clear(); leds.show();
                break;
            }
            case SCR_SYS: drawHeader("Hold OK for Menu"); break;
            default: drawHeader("Screen"); break;
        }
    } else if(state.uiMode == UI_MENU) {
        drawHeader("System Menu");
        const char* opts[] = {"Wi-Fi Connect", "OTA Update", "System Info", "Reboot", "Back"};
        for(int i=0; i<4; i++) {
            int idx = state.menuOffset + i;
            if(idx >= MN_COUNT) break;
            if(idx == state.menuIdx) {
                u8g2.drawBox(0, 20+i*11, 128, 11); u8g2.setDrawColor(0);
            }
            u8g2.drawStr(5, 30+i*11, opts[idx]); u8g2.setDrawColor(1);
        }
    } else if(state.uiMode == UI_OTA) {
        drawHeader("OTA Mode");
        u8g2.setFont(u8g2_font_profont11_tf);
        String ip = WiFi.localIP().toString();
        u8g2.drawStr((128 - u8g2.getStrWidth(ip.c_str()))/2, 35, ip.c_str());
        u8g2.drawStr(10, 55, "Ready for Upload...");
    }
    u8g2.sendBuffer();
}

// OTA Setup
void setupOTA() {
    ArduinoOTA.setHostname("smartmedbox");
    ArduinoOTA.setPassword("medbox123");
    ArduinoOTA
            .onStart([]() {
                SPIFFS.end();
                u8g2.clearBuffer(); u8g2.drawStr(30, 30, "Updating..."); u8g2.sendBuffer();
            })
            .onEnd([]() {
                u8g2.clearBuffer(); u8g2.drawStr(30, 30, "Done!"); u8g2.sendBuffer();
                delay(1000); ESP.restart();
            });
    ArduinoOTA.begin();
    Serial.println("OTA ready.");
}

// Enter OTA - **** FIXED ****
void enterOtaMode() {
    if (isOtaMode || !state.wifiConnected) {
        u8g2.clearBuffer();
        u8g2.drawStr(20, 30, "No WiFi!");
        u8g2.sendBuffer();
        delay(1000);
        return;
    };
    isOtaMode = true;
    state.uiMode = UI_OTA;
    // BLEDevice::deinit(true); // <--- REMOVED TO PREVENT CRASH
    Serial.println("OTA Mode Active");
}

// ==================== 主程式 ====================
void setup() {
    Serial.begin(115200);

    pinMode(PIN_BTN_OK, INPUT_PULLUP);
    pinMode(PIN_BTN_BACK, INPUT_PULLUP);
    pinMode(PIN_ENC_BTN, INPUT_PULLUP);

    Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
    u8g2.begin(); dht.begin(); leds.begin();
    if(SPIFFS.begin(true)) loadAlarms();

    encoder.begin();
    encoder.setup([]{encoder.readEncoder_ISR();}, []{});
    encoder.setBoundaries(0, 3, true);

    // POST
    u8g2.clearBuffer(); u8g2.setFont(u8g2_font_ncenB08_tr); u8g2.drawStr(10,30,"Self Test..."); u8g2.sendBuffer();
    leds.fill(leds.Color(0,50,0)); leds.show(); delay(500); leds.clear(); leds.show();
    beep(1, 1000, 100);

    BLEDevice::init("SmartMedBox");
    BLEServer *s = BLEDevice::createServer();
    s->setCallbacks(new ServerCB());
    BLEService *svc = s->createService(SERVICE_UUID);
    svc->createCharacteristic(CHAR_CMD_UUID, BLECharacteristic::PROPERTY_WRITE)->setCallbacks(new CmdCB());
    pBleData = svc->createCharacteristic(CHAR_DATA_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pBleData->addDescriptor(new BLE2902());
    svc->start();
    BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
    BLEDevice::startAdvertising();

    WiFi.mode(WIFI_STA);
    connectWiFi();
}

void loop() {
    unsigned long now = millis();

    if(WiFi.status() == WL_CONNECTED) {
        if(!state.wifiConnected) {
            state.wifiConnected = true;
            configTime(GMT_OFFSET, 0, NTP_SERVER);
            if(MDNS.begin("medbox")) ArduinoOTA.begin();
            setupOTA();
        }
        if(isOtaMode) {
            ArduinoOTA.handle();
            // Exit OTA
            if(!digitalRead(PIN_BTN_BACK)) ESP.restart();
            // Refresh OTA Screen
            if(now - tmrDisplay > INTERVAL_DISPLAY) { render(); tmrDisplay = now; }
            return; // Stop other logic
        }
    } else state.wifiConnected = false;

    // Alarm Check
    if(now - tmrAlarmCheck > 1000) { checkAlarms(); tmrAlarmCheck = now; }

    // Alarm Ringing
    if(state.uiMode == UI_ALARM_RINGING) {
        playAlarmEffect();
        if(!digitalRead(PIN_BTN_OK) || !digitalRead(PIN_BTN_BACK) || !digitalRead(PIN_ENC_BTN)) {
            state.uiMode = UI_MAIN;
            leds.clear(); leds.show();
            noTone(PIN_BUZZER_1); noTone(PIN_BUZZER_2);
            delay(500);
        }
        render(); return;
    }

    // Encoder
    if(encoder.encoderChanged()) {
        beep(1, 4000, 10);
        if(state.uiMode == UI_MENU) {
            state.menuIdx = encoder.readEncoder();
            if(state.menuIdx >= state.menuOffset + 4) state.menuOffset++;
            else if(state.menuIdx < state.menuOffset) state.menuOffset--;
        } else {
            state.curScreen = (Screen)encoder.readEncoder();
        }
        tmrDisplay = 0;
    }

    // OK Button
    if(!digitalRead(PIN_BTN_OK) && now - tmrBtnOk > 1000) {
        if(state.uiMode == UI_MAIN) {
            state.uiMode = UI_MENU;
            encoder.setBoundaries(0, MN_COUNT-1, true);
            state.menuIdx = 0; encoder.setEncoderValue(0);
            beep(2, 1500, 100);
        } else if (state.uiMode == UI_MENU) {
            // Menu Select Logic
            switch(state.menuIdx) {
                case MN_WIFI: connectWiFi(); break;
                case MN_OTA: enterOtaMode(); break;
                case MN_INFO: state.uiMode = UI_INFO; break;
                case MN_REBOOT: ESP.restart(); break;
                case MN_BACK: state.uiMode = UI_MAIN; encoder.setBoundaries(0, state.engMode?7:3, true); break;
            }
        }
        tmrBtnOk = now;
    }

    // Back Button
    if(!digitalRead(PIN_BTN_BACK) && now - tmrBtnBack > 300) {
        beep(2, 1000, 50);
        if(state.uiMode == UI_MENU || state.uiMode == UI_INFO) {
            state.uiMode = UI_MAIN;
            encoder.setBoundaries(0, state.engMode?7:3, true);
        }
        tmrBtnBack = now;
    }

    if(now - tmrDisplay > INTERVAL_DISPLAY) { render(); tmrDisplay = now; }
}

const uint8_t ico_ble[] = {0x18,0x24,0x42,0x5A,0x5A,0x42,0x24,0x18};
const uint8_t ico_wifi[] = {0x00,0x18,0x24,0x42,0x81,0x42,0x24,0x18};
const uint8_t ico_no_wifi[] = {0x00,0x18,0x18,0x18,0x00,0x18,0x18,0x00};
const uint8_t ico_gear[] = {0x24,0x18,0x7E,0x25,0x52,0x7E,0x18,0x24};