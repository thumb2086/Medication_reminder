#include "globals.h"
#include <Update.h> // <--- 新增
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <sys/time.h>

// Pre-declare functions from other modules that are used here
void updateScreens();
void guideToSlot(int slot);

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleDeviceConnected = true;
        Serial.println("DEBUG: BLE Client Connected");
    }
    void onDisconnect(BLEServer* pServer) {
        bleDeviceConnected = false;
        isRealtimeEnabled = false;
        isBleOtaInProgress = false; // <--- 新增: 如果中途斷線，終止 OTA
        Serial.println("DEBUG: BLE Client Disconnected");
        BLEDevice::startAdvertising();
    }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            // Serial.printf("DEBUG: BLE command received, length: %d\n", value.length()); // Can be verbose
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
}

void handleCommand(uint8_t* data, size_t length) {
    if (length == 0) return;
    uint8_t command = data[0];
    
    // For OTA data, avoid printing every packet to prevent log spam
    if (command != CMD_OTA_DATA) {
        Serial.printf("BLE RX: CMD=0x%02X, Len=%d\n", command, length);
    }

    switch (command) {
        case CMD_PROTOCOL_VERSION:
            if (length == 1) {
                Serial.println("DEBUG: CMD_PROTOCOL_VERSION received.");
                uint8_t packet[2] = {CMD_REPORT_PROTO_VER, 2};
                pDataEventCharacteristic->setValue(packet, 2);
                pDataEventCharacteristic->notify();
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
                }
            }
            sendTimeSyncAck();
            break;
        case CMD_GUIDE_PILLBOX:
            if (length == 2) {
                uint8_t slot = data[1];
                Serial.printf("DEBUG: CMD_GUIDE_PILLBOX received for slot %d\n", slot);
                guideToSlot(slot);
            }
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
            sendTimeSyncAck();
            break;
        case CMD_DISABLE_REALTIME:
            Serial.println("DEBUG: CMD_DISABLE_REALTIME received.");
            isRealtimeEnabled = false;
            sendTimeSyncAck();
            break;

        // =======================================
        // ===== 新增的 BLE OTA 處理邏輯 Start =====
        // =======================================
        case CMD_OTA_START: {
            if (length < 5) {
                Serial.println("ERROR: CMD_OTA_START packet too short!");
                sendErrorReport(0x05); // Length error
                break;
            }
            // 從封包中解析韌體總大小 (4 bytes, little-endian)
            otaTotalSize = (data[4] << 24) | (data[3] << 16) | (data[2] << 8) | data[1];
            otaBytesReceived = 0;
            
            Serial.printf("DEBUG: CMD_OTA_START received. Total size: %u bytes\n", otaTotalSize);

            // Display OTA message on screen
            u8g2.clearBuffer();
            u8g2.setFont(u8g2_font_ncenB10_tr);
            u8g2.drawStr((128 - u8g2.getStrWidth("BLE OTA"))/2, 20, "BLE OTA");
            u8g2.setFont(u8g2_font_ncenB08_tr);
            u8g2.drawStr((128 - u8g2.getStrWidth("Receiving..."))/2, 40, "Receiving...");
            u8g2.sendBuffer();

            if (Update.begin(otaTotalSize)) {
                isBleOtaInProgress = true;
                otaStartTime = millis();
                Serial.println("DEBUG: OTA Update process started.");
            } else {
                Serial.println("ERROR: Not enough space to begin OTA");
                Update.printError(Serial);
                sendErrorReport(0x04); // Access error / insufficient space
            }
            break;
        }

        case CMD_OTA_DATA: {
            if (!isBleOtaInProgress) {
                Serial.println("ERROR: CMD_OTA_DATA received without START.");
                sendErrorReport(0x03); // Unknown command / wrong sequence
                break;
            }
            
            size_t chunkSize = length - 1;
            
            if (chunkSize > 0) {
                size_t bytesWritten = Update.write(&data[1], chunkSize);
                if (bytesWritten == chunkSize) {
                    otaBytesReceived += bytesWritten;
                    // Update progress on screen
                    int progress = (int)((otaBytesReceived * 100) / otaTotalSize);
                    u8g2.drawBox(0, 50, 128, 10);
                    u8g2.setDrawColor(0); // color 0 for the text
                    char progressStr[5];
                    sprintf(progressStr, "%d%%", progress);
                    u8g2.drawStr((128 - u8g2.getStrWidth(progressStr))/2, 60, progressStr);
                    u8g2.setDrawColor(1); // Back to default
                    u8g2.drawBox(2, 52, (124 * progress) / 100, 6);
                    u8g2.sendBuffer();

                } else {
                    Serial.println("ERROR: OTA data write failed!");
                    Update.printError(Serial);
                    isBleOtaInProgress = false;
                    sendErrorReport(0x04); // Access error
                }
            }
            break;
        }

        case CMD_OTA_END: {
            if (!isBleOtaInProgress) {
                Serial.println("ERROR: CMD_OTA_END received without START.");
                sendErrorReport(0x03); // Unknown command / wrong sequence
                break;
            }
            
            Serial.printf("DEBUG: CMD_OTA_END received. Total bytes received: %u\n", otaBytesReceived);

            if (Update.end(true)) { 
                Serial.printf("SUCCESS: OTA Update successful in %lu ms. Rebooting...\n", millis() - otaStartTime);
                u8g2.clearBuffer();
                u8g2.setFont(u8g2_font_ncenB08_tr);
                u8g2.drawStr((128 - u8g2.getStrWidth("Update OK!"))/2, 20, "Update OK!");
                u8g2.drawStr((128 - u8g2.getStrWidth("Rebooting..."))/2, 40, "Rebooting...");
                u8g2.sendBuffer();
                delay(2000);
                ESP.restart();
            } else {
                Serial.println("ERROR: OTA Update failed!");
                Update.printError(Serial);
                isBleOtaInProgress = false;
                u8g2.clearBuffer();
                u8g2.setFont(u8g2_font_ncenB08_tr);
                u8g2.drawStr((128 - u8g2.getStrWidth("Update Failed!"))/2, 38, "Update Failed!");
                u8g2.sendBuffer();
                delay(3000);
                // Optionally restart or return to main screen
                // ESP.restart(); 
                sendErrorReport(0x04); 
            }
            break;
        }
        // =======================================
        // ===== 新增的 BLE OTA 處理邏輯 End =======
        // =======================================

        default:
            Serial.printf("Error: Unknown Command 0x%02X\n", command);
            sendErrorReport(0x03);
            break;
    }
}

void sendBoxStatus() {
    if (!bleDeviceConnected) return;
    // Serial.println("DEBUG: Sending box status.");
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
    // Serial.println("DEBUG: Sending sensor data report.");
    int16_t t_val = (int16_t)(cachedTemp * 100);
    int16_t h_val = (int16_t)(cachedHum * 100);
    uint8_t packet[5];
    packet[0] = CMD_REPORT_ENV;
    memcpy(&packet[1], &t_val, 2);
    memcpy(&packet[3], &h_val, 2);
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
    memcpy(&packet[1], &t_val, 2);
    memcpy(&packet[3], &h_val, 2);
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
    const int MAX_POINTS_PER_PACKET = 2; // Reduced for MTU size safety
    uint8_t batchPacket[1 + MAX_POINTS_PER_PACKET * 8];
    uint8_t pointsInBatch = 0;
    int packetWriteIndex = 1;
    while (pointsInBatch < MAX_POINTS_PER_PACKET && historicDataIndexToSend < historyCount) {
        DataPoint dp;
        int startIdx = (historyIndex - historyCount + MAX_HISTORY) % MAX_HISTORY;
        int currentReadIdx = (startIdx + historicDataIndexToSend) % MAX_HISTORY;
        historyFile.seek(currentReadIdx * sizeof(DataPoint));
        historyFile.read((uint8_t*)&dp, sizeof(DataPoint));
        time_t timestamp = time(nullptr) - (historyCount - 1 - historicDataIndexToSend) * (historyRecordInterval / 1000);
        
        memcpy(&batchPacket[packetWriteIndex], &timestamp, 4);
        packetWriteIndex += 4;
        
        int16_t t_val = (int16_t)(dp.temp * 100);
        int16_t h_val = (int16_t)(dp.hum * 100);
        
        memcpy(&batchPacket[packetWriteIndex], &t_val, 2);
        packetWriteIndex += 2;
        memcpy(&batchPacket[packetWriteIndex], &h_val, 2);
        packetWriteIndex += 2;
        
        pointsInBatch++;
        historicDataIndexToSend++;
    }
    if (pointsInBatch > 0) {
        batchPacket[0] = CMD_REPORT_HISTORIC_POINT;
        pDataEventCharacteristic->setValue(batchPacket, 1 + pointsInBatch * 8);
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
    // Serial.println("DEBUG: Sending Time Sync ACK.");
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
