
#include "globals.h"
#include <Wire.h>

// Helper function to drive the servo to a specific angle using native LEDC
void runServo(int angle) {
    const int servoFreq = 50;       // Standard servo frequency
    const int servoResolution = 16;   // 16-bit resolution for precise control

    // Attach LEDC to the servo pin. On ESP32-C6 core 3.0+, this also sets up the channel.
    if (!ledcAttach(SERVO_PIN, servoFreq, servoResolution)) {
        Serial.println("ERROR: LEDC attach failed for Servo!");
        return;
    }

    // Clamp the angle to the valid range
    angle = constrain(angle, 0, 180);

    // Map angle (0-180) to 16-bit duty cycle (0-65535)
    // SG90 standard pulse width: 500us (0 deg) to 2500us (180 deg)
    // For 50Hz, period is 20000us.
    // Duty for 0 deg: (500 / 20000) * 65535 = 1638.375 -> 1638
    // Duty for 180 deg: (2500 / 20000) * 65535 = 8191.875 -> 8192
    int duty = map(angle, 0, 180, 1638, 8192);
    
    ledcWrite(SERVO_PIN, duty);
    // Serial.printf("Servo -> %d degrees (Duty: %d)\n", angle, duty);

    // A short delay is crucial for the servo to reach the position before detaching.
    delay(500);
    // Detach the pin to stop sending the signal, which prevents servo jitter and saves power.
    ledcDetach(SERVO_PIN);
}

void guideToSlot(int slot) {
    if (slot < 1 || slot > 8) {
        Serial.printf("Error: Invalid slot number %d for guide.\n", slot);
        return;
    }
    // Assuming 8 slots spread over 180 degrees.
    // Slot 1 -> 0 deg, Slot 8 -> 180 deg.
    // Angle per slot = 180 / 7 = ~25.71
    int angle = map(slot, 1, 8, 0, 180);
    angle = constrain(angle, 0, 180);
    
    Serial.printf("Guiding to slot %d (angle: %d)\n", slot, angle);
    runServo(angle);
}

void runPOST() {
    Serial.println("DEBUG: runPOST - Starting Power-On Self-Test");
    // Initialize low-power components
    pixels.begin();
    pixels.setBrightness(5); // Lowered brightness to reduce eye strain
    pixels.clear();
    pixels.show();

    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(BUZZER_PIN, LOW);
    pinMode(BUZZER_PIN_2, OUTPUT);
    digitalWrite(BUZZER_PIN_2, LOW);

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("Hardware Check...")) / 2, 38, "Hardware Check...");
    u8g2.sendBuffer();
    delay(500);

    // Test LED and Buzzer (even if disconnected)
    Serial.println("DEBUG: Testing RGB LED strip.");
    pixels.fill(pixels.Color(255, 0, 0)); pixels.show(); delay(300);
    pixels.fill(pixels.Color(0, 255, 0)); pixels.show(); delay(300);
    pixels.fill(pixels.Color(0, 0, 255)); pixels.show(); delay(300);
    pixels.clear(); pixels.show();

    Serial.println("DEBUG: Testing buzzer.");
    tone(BUZZER_PIN, 1000, 100);
    tone(BUZZER_PIN_2, 1000, 100);
    delay(200);
    tone(BUZZER_PIN, 1500, 100);
    tone(BUZZER_PIN_2, 1500, 100);
    delay(200);

    // Perform the motor test using the new reliable method
    Serial.println("DEBUG: Testing motor with user-validated LEDC method.");
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("Motor Test...")) / 2, 38, "Motor Test...");
    u8g2.sendBuffer();

    runServo(0);
    delay(500); // Wait between movements
    runServo(180);
    delay(500);
    runServo(0);
    delay(500);

    // Final "OK" message
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr((128 - u8g2.getStrWidth("Check OK")) / 2, 38, "Check OK");
    u8g2.sendBuffer();
    delay(1000);
    Serial.println("DEBUG: POST finished.");
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
            sensorDataValid = false;
            Serial.println("DEBUG: Failed to read from DHT sensor!");
            Serial.println("Failed to read from DHT sensor!");
        }
    }
}

void checkAlarm() {
    if (!alarmEnabled || isAlarmRinging) return;
    
    // 檢查時間間隔，避免每毫秒都呼叫 getLocalTime (這在某些 BSP 上可能較慢)
    if (millis() - lastAlarmCheckTime < 1000) return;
    lastAlarmCheckTime = millis();
    
    struct tm timeinfo;
    // 取得本地時間，如果 NTP 還沒同步，這個函式會失敗
    if (!getLocalTime(&timeinfo)) {
        // Serial.println("DEBUG: getLocalTime failed in checkAlarm");
        return;
    }
    
    // 只在秒數為 0 的那一秒觸發一次
    if (timeinfo.tm_hour == alarmHour && timeinfo.tm_min == alarmMinute && timeinfo.tm_sec == 0) {
        Serial.printf("DEBUG: ALARM TRIGGERED! Current: %02d:%02d:%02d, Target: %02d:%02d\n", 
                      timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec, alarmHour, alarmMinute);
        isAlarmRinging = true;
        // 播放確認音效 (這包含 delay(60)，但在 loop 中偶爾一次還好)
        playConfirmSound();
    }
}
