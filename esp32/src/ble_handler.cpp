
#include "globals.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <sys/time.h>

// Pre-declare functions from other modules that are used here
void updateScreens();

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleDeviceConnected = true;
        Serial.println("DEBUG: BLE Client Connected");
        Serial.println("BLE Connected");
    }
    void onDisconnect(BLEServer* pServer) {
        bleDeviceConnected = false;
        isRealtimeEnabled = false;
        Serial.println("DEBUG: BLE Client Disconnected");
        Serial.println("BLE Disconnected");
        BLEDevice::startAdvertising();
    }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String value = pCharacteristic->getValue();
        if (value.length() > 0) {
            Serial.printf("DEBUG: BLE command received, length: %d\n", value.length());
            handleCommand((uint8_t*)value.c_str(), value.length());
        }
    }
};

void setupBLE() {
    Serial.println("DEBUG: setupBLE");
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
    Serial.println("DEBUG: BLE Server started and advertising.");
    Serial.println("BLE Server started.");
}

void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t command = data[0];
    Serial.printf("BLE RX: CMD=0x%02X, Len=%d\n", command, length);

    switch (command) {
        case CMD_PROTOCOL_VERSION:
            if (length == 1) {
                Serial.println("DEBUG: CMD_PROTOCOL_VERSION received.");
                uint8_t packet[2] = {CMD_REPORT_PROTO_VER, 2};
                pDataEventCharacteristic->setValue(packet, 2);
                pDataEventCharacteristic->notify();
                Serial.println("Protocol Version 2 reported.");
            }
            break;
        case CMD_TIME_SYNC:
            if (length == 7) {
                Serial.println("DEBUG: CMD_TIME_SYNC received.");
                tm timeinfo;
                timeinfo.tm_year = data[1] + 100; timeinfo.tm_mon  = data[2] - 1; timeinfo.tm_mday = data[3];
                timeinfo.tm_hour = data[4]; timeinfo.tm_min  = data[5]; timeinfo.tm_sec  = data[6];
                time_t t = mktime(&timeinfo);
                timeval tv = {t, 0};
                settimeofday(&tv, nullptr);
                syncIconStartTime = millis();
                sendTimeSyncAck();
                Serial.println("DEBUG: Time synced from BLE.");
            }
            break;
        case CMD_WIFI_CREDENTIALS:
            if (length >= 3) {
                uint8_t ssidLen = data[1];
                uint8_t passLen = data[2 + ssidLen];
                if (length == 3 + ssidLen + passLen) {
                    String newSSID = String((char*)&data[2], ssidLen);
                    String newPASS = String((char*)&data[3 + ssidLen], passLen);
                    Serial.printf("DEBUG: CMD_WIFI_CREDENTIALS received. SSID: %s\n", newSSID.c_str());
                    preferences.begin("wifi", false);
                    preferences.putString("ssid", newSSID);
                    preferences.putString("pass", newPASS);
                    preferences.end();
                    startWiFiConnection();
                    sendTimeSyncAck();
                }
            }
            break;
        case CMD_SET_ENGINEERING_MODE:
            if (length == 2) {
                isEngineeringMode = (data[1] == 0x01);
                Serial.printf("DEBUG: CMD_SET_ENGINEERING_MODE received. Mode: %s\n", isEngineeringMode ? "ON" : "OFF");
                preferences.begin("medbox-meta", false);
                preferences.putBool("engMode", isEngineeringMode);
                preferences.end();
                updateScreens();
                sendTimeSyncAck();
            }
            break;
        case CMD_REQUEST_ENG_MODE_STATUS:
            if (length == 1) {
                Serial.println("DEBUG: CMD_REQUEST_ENG_MODE_STATUS received.");
                uint8_t status = isEngineeringMode ? 0x01 : 0x00;
                uint8_t packet[2] = {CMD_REPORT_ENG_MODE_STATUS, status};
                pDataEventCharacteristic->setValue(packet, 2);
                pDataEventCharacteristic->notify();
            }
            break;
        case CMD_SET_ALARM:
            if (length >= 4) {
                uint8_t newHour = data[1];
                uint8_t newMinute = data[2];
                bool newEnabled = (data[3] != 0);
                if (newHour < 24 && newMinute < 60) {
                    alarmHour = newHour;
                    alarmMinute = newMinute;
                    alarmEnabled = newEnabled;
                    preferences.begin("medbox-meta", false);
                    preferences.putUChar("alarmH", alarmHour);
                    preferences.putUChar("alarmM", alarmMinute);
                    preferences.putBool("alarmOn", alarmEnabled);
                    preferences.end();
                    Serial.printf("DEBUG: Alarm set to %02d:%02d, Enabled: %d\n", alarmHour, alarmMinute, alarmEnabled);
                    Serial.printf("Alarm Set: %02d:%02d, Enabled: %s\n", alarmHour, alarmMinute, alarmEnabled ? "ON" : "OFF");
                }
            }
            sendTimeSyncAck();
            break;
        case CMD_REQUEST_STATUS:
            Serial.println("DEBUG: CMD_REQUEST_STATUS received.");
            sendBoxStatus();
            break;
        case CMD_REQUEST_ENV:
            Serial.println("DEBUG: CMD_REQUEST_ENV received.");
            sendSensorDataReport();
            break;
        case CMD_REQUEST_HISTORIC:
            Serial.println("DEBUG: CMD_REQUEST_HISTORIC received.");
            if (!isSendingHistoricData) {
                isSendingHistoricData = true;
                historicDataIndexToSend = 0;
                historicDataStartTime = millis();
                Serial.println("Starting historic data transfer (batch mode)...");
            }
            break;
        case CMD_ENABLE_REALTIME:
            Serial.println("DEBUG: CMD_ENABLE_REALTIME received.");
            isRealtimeEnabled = true;
            Serial.println("Real-time data enabled.");
            sendTimeSyncAck();
            break;
        case CMD_DISABLE_REALTIME:
            Serial.println("DEBUG: CMD_DISABLE_REALTIME received.");
            isRealtimeEnabled = false;
            Serial.println("Real-time data disabled.");
            sendTimeSyncAck();
            break;
        default:
            Serial.printf("Error: Unknown Command 0x%02X\n", command);
            sendErrorReport(0x03);
            break;
    }
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    Serial.println("DEBUG: Sending box status.");
    uint8_t packet[2] = {CMD_REPORT_STATUS, 0b00001111};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendMedicationTaken(uint8_t slot) {
    if (!bleDeviceConnected || slot > 7) return;
    Serial.printf("DEBUG: Sending medication taken for slot %d.\n", slot);
    uint8_t packet[2] = {CMD_REPORT_TAKEN, slot};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void sendSensorDataReport() {
    if (!bleDeviceConnected) return;
    if (!sensorDataValid) {
        Serial.println("DEBUG: Sensor data not valid, sending error report.");
        sendErrorReport(0x02);
        return;
    }
    Serial.println("DEBUG: Sending sensor data report.");
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
    // This function is called frequently, so debug message is commented out.
    // Serial.println("DEBUG: Sending real-time sensor data."); 
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
    Serial.println("DEBUG: Sending end of historic data transfer.");
    uint8_t packet[1] = {CMD_REPORT_HISTORIC_END};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void handleHistoricDataTransfer() {
    if (!isSendingHistoricData) return;
    // Serial.println("DEBUG: handleHistoricDataTransfer");
    if (historicDataIndexToSend == 0) {
        historyFile = SPIFFS.open("/history.dat", "r");
        if (!historyFile) {
            Serial.println("DEBUG: Failed to open history file for transfer.");
            sendErrorReport(0x04);
            isSendingHistoricData = false;
            return;
        }
    }
    if (!bleDeviceConnected) {
        historyFile.close();
        isSendingHistoricData = false;
        Serial.println("BLE disconnected during transfer. Aborting.");
        return;
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
        // Serial.printf("DEBUG: Sending batch of %d historic data points.\n", pointsInBatch);
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
    Serial.println("DEBUG: Sending Time Sync ACK.");
    uint8_t packet[1] = {CMD_TIME_SYNC_ACK};
    pDataEventCharacteristic->setValue(packet, 1);
    pDataEventCharacteristic->notify();
}

void sendErrorReport(uint8_t errorCode) {
    if (!bleDeviceConnected) return;
    Serial.printf("DEBUG: Sending error report with code 0x%02X.\n", errorCode);
    uint8_t packet[2] = {CMD_ERROR, errorCode};
    pDataEventCharacteristic->setValue(packet, 2);
    pDataEventCharacteristic->notify();
}

void handleRealtimeData() {
    if (isRealtimeEnabled && bleDeviceConnected && (millis() - lastRealtimeSend > REALTIME_INTERVAL)) {
        lastRealtimeSend = millis();
        sendRealtimeSensorData();
    }
}
