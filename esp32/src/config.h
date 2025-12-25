
#pragma once

#include <Arduino.h>

// ==================== 韌體版本 ====================
#define FIRMWARE_VERSION "v22.1"

// ==================== 硬體與儲存常數 ====================
#define MAX_HISTORY 4800
#define HISTORY_WINDOW_SIZE 60

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
#define BUZZER_PIN 10
#define BUZZER_PIN_2 11
#define SERVO_PIN 3
#define WS2812_PIN 15
#define NUM_LEDS 64

// ==================== Wi-Fi & NTP & OTA (宣告) ====================
extern const char* default_ssid;
extern const char* default_password;
extern String openWeatherMapApiKey;
extern String city;
extern String countryCode;
extern const float TEMP_CALIBRATION_OFFSET;
extern const char* NTP_SERVER;
extern const long GMT_OFFSET;
extern const int DAYLIGHT_OFFSET;

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

// ==================== 圖示 (XBM) ====================
static const unsigned char icon_ble_bits[] U8X8_PROGMEM = {0x18, 0x24, 0x42, 0x5A, 0x5A, 0x42, 0x24, 0x18};
static const unsigned char icon_sync_bits[] U8X8_PROGMEM = {0x00, 0x3C, 0x46, 0x91, 0x11, 0x26, 0x3C, 0x00};
static const unsigned char icon_wifi_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x24, 0x42, 0x81, 0x42, 0x24, 0x18};
static const unsigned char icon_wifi_fail_bits[] U8X8_PROGMEM = {0x00, 0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x00};
static const unsigned char icon_gear_bits[] U8X8_PROGMEM = {0x24, 0x18, 0x7E, 0x25, 0x52, 0x7E, 0x18, 0x24};
static const unsigned char icon_wifi_connecting_bits[] U8X8_PROGMEM = {0x00,0x00,0x0E,0x11,0x11,0x0E,0x00,0x00};
