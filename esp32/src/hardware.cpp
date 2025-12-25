
#include "globals.h"
#include <Wire.h>

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

void checkAlarm() {
    if (!alarmEnabled || isAlarmRinging) return;
    if (millis() - lastAlarmCheckTime < 1000) return;
    lastAlarmCheckTime = millis();
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) return;
    if (timeinfo.tm_hour == alarmHour && timeinfo.tm_min == alarmMinute && timeinfo.tm_sec == 0) {
        Serial.println("ALARM TRIGGERED!");
        isAlarmRinging = true;
        playConfirmSound();
    }
}
