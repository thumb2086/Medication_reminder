
#include "globals.h"

void initializeHistoryFile() {
    Serial.println("DEBUG: initializeHistoryFile");
    if (!SPIFFS.exists("/history.dat")) {
        Serial.println("DEBUG: /history.dat not found, creating new file.");
        File file = SPIFFS.open("/history.dat", FILE_WRITE); 
        if (!file) {
            Serial.println("DEBUG: Failed to create /history.dat");
            return;
        }
        DataPoint empty = {0, 0, 0}; 
        for (int i = 0; i < MAX_HISTORY; i++) { 
            file.write((uint8_t*)&empty, sizeof(DataPoint)); 
        } 
        file.close();
        Serial.println("DEBUG: /history.dat created successfully.");
    } else {
        Serial.println("DEBUG: /history.dat already exists.");
    }
}

void loadHistoryMetadata() {
    Serial.println("DEBUG: loadHistoryMetadata");
    preferences.begin("medbox-meta", true);
    historyCount = preferences.getInt("hist_count", 0);
    historyIndex = preferences.getInt("hist_index", 0);
    preferences.end();
    Serial.printf("DEBUG: Loaded history metadata - Count: %d, Index: %d\n", historyCount, historyIndex);
}

void addDataToHistory(float temp, float hum, int16_t rssi) {
    Serial.println("DEBUG: addDataToHistory");
    File file = SPIFFS.open("/history.dat", "r+");
    if (!file) {
        Serial.println("DEBUG: Failed to open /history.dat for writing.");
        return;
    }
    DataPoint dp = {temp, hum, rssi};
    file.seek(historyIndex * sizeof(DataPoint));
    file.write((uint8_t*)&dp, sizeof(DataPoint));
    file.close();
    historyIndex = (historyIndex + 1) % MAX_HISTORY;
    if (historyCount < MAX_HISTORY) historyCount++;
    Serial.printf("DEBUG: Added data to history at index %d. New count: %d\n", (historyIndex - 1 + MAX_HISTORY) % MAX_HISTORY, historyCount);
    preferences.begin("medbox-meta", false);
    preferences.putInt("hist_count", historyCount);
    preferences.putInt("hist_index", historyIndex);
    preferences.putFloat("last_temp", temp);
    preferences.putFloat("last_hum", hum);
    preferences.end();
    if (currentEncoderMode == MODE_VIEW_ADJUST) {
        int maxOffset = max(0, historyCount - HISTORY_WINDOW_SIZE);
        rotaryEncoder.setBoundaries(0, maxOffset, false);
    }
}

void loadHistoryWindow(int offset) {
    Serial.printf("DEBUG: loadHistoryWindow with offset %d\n", offset);
    int points = min(historyCount, HISTORY_WINDOW_SIZE); 
    if (points == 0) {
        Serial.println("DEBUG: No history points to load.");
        return;
    }
    File file = SPIFFS.open("/history.dat", "r"); 
    if (!file) {
        Serial.println("DEBUG: Failed to open /history.dat for reading.");
        return;
    }
    int startIdx = (historyIndex - offset - points + MAX_HISTORY) % MAX_HISTORY;
    Serial.printf("DEBUG: Loading %d points starting from index %d\n", points, startIdx);
    for (int i = 0; i < points; i++) {
        int idx = (startIdx + i) % MAX_HISTORY;
        file.seek(idx * sizeof(DataPoint)); 
        file.read((uint8_t*)&historyWindowBuffer[i], sizeof(DataPoint));
    }
    file.close();
    Serial.println("DEBUG: History window loaded.");
}

void loadPersistentStates() {
    Serial.println("DEBUG: loadPersistentStates");
    preferences.begin("medbox-meta", true);
    isEngineeringMode = preferences.getBool("engMode", false);
    alarmHour = preferences.getUChar("alarmH", 0);
    alarmMinute = preferences.getUChar("alarmM", 0);
    alarmEnabled = preferences.getBool("alarmOn", false);
    float savedTemp = preferences.getFloat("last_temp", 0.0);
    float savedHum = preferences.getFloat("last_hum", 0.0);
    Serial.printf("DEBUG: Loaded persistent states - EngMode: %d, Alarm: %02d:%02d (%d)\n", isEngineeringMode, alarmHour, alarmMinute, alarmEnabled);
    if (savedTemp != 0.0 || savedHum != 0.0) {
        cachedTemp = savedTemp;
        cachedHum = savedHum;
        sensorDataValid = true;
        Serial.printf("Restored last sensor data: T=%.1f, H=%.1f\n", cachedTemp, cachedHum);
    }
    preferences.end();
}
