
#pragma once

void initializeHistoryFile();
void loadHistoryMetadata();
void addDataToHistory(float temp, float hum, int16_t rssi);
void loadHistoryWindow(int offset);
void loadPersistentStates();
