
#pragma once

#include <Arduino.h>

void setupBLE();
void handleCommand(uint8_t* data, size_t length);
void sendBoxStatus();
void sendMedicationTaken(uint8_t slot);
void sendSensorDataReport();
void sendRealtimeSensorData();
void sendHistoricDataEnd();
void sendTimeSyncAck();
void sendErrorReport(uint8_t errorCode);
void handleHistoricDataTransfer();
void handleRealtimeData();
