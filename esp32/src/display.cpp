
#include "globals.h"
#include <time.h>
#include <WiFi.h> // <--- Added this include

// Pre-declare functions from other modules that are used here
void loadHistoryWindow(int offset);

void updateDisplay() {
    // This function is called frequently, so debug messages are commented out by default.
    // Serial.println("DEBUG: updateDisplay"); 
    lastDisplayUpdate = millis(); 
    u8g2.clearBuffer();
    switch (currentUIMode) {
        case UI_MODE_MAIN_SCREENS:
            // Serial.printf("DEBUG: Drawing main screen, index: %d\n", currentPageIndex);
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
        case UI_MODE_SYSTEM_MENU: 
            // Serial.println("DEBUG: Drawing system menu.");
            drawSystemMenu(); 
            break;
        case UI_MODE_INFO_SCREEN: 
            // Serial.println("DEBUG: Drawing info screen.");
            drawSystemScreen(); 
            break;
    }
    drawStatusIcons(); 
    u8g2.sendBuffer();
}

void drawSystemMenu() {
    u8g2.setFont(u8g2_font_ncenB08_tr); 
    u8g2.drawStr((128 - u8g2.getStrWidth("System Menu")) / 2, 10, "System Menu");
    const char* menuItems[] = { "Connect to WiFi", "OTA Update", "System Info", "Reboot Device", "Back to Main" };
    for (int i = 0; i < MAX_MENU_ITEMS_ON_SCREEN; i++) {
        int itemIndex = menuViewOffset + i;
        if (itemIndex < NUM_MENU_ITEMS) {
            int y = 24 + i * 11;
            if (itemIndex == selectedMenuItem) { 
                u8g2.drawBox(0, y - 9, 128 - 6, 11); 
                u8g2.setDrawColor(0); 
                u8g2.drawStr(5, y, menuItems[itemIndex]); 
                u8g2.setDrawColor(1); 
            } else { 
                u8g2.drawStr(5, y, menuItems[itemIndex]); 
            }
        }
    }
    if (NUM_MENU_ITEMS > MAX_MENU_ITEMS_ON_SCREEN) {
        int sbX = 122, sbY = 18, sbW = 4, sbH = 44; 
        u8g2.drawFrame(sbX, sbY, sbW, sbH);
        int handleH = max(3, (int)((float)sbH * MAX_MENU_ITEMS_ON_SCREEN / NUM_MENU_ITEMS));
        int handleY = sbY + (int)((float)(sbH - handleH) * menuViewOffset / (NUM_MENU_ITEMS - MAX_MENU_ITEMS_ON_SCREEN));
        u8g2.drawBox(sbX, handleY, sbW, handleH);
    }
}

void drawOtaScreen(String text, int progress) {
    Serial.printf("DEBUG: drawOtaScreen - Text: %s, Progress: %d\n", text.c_str(), progress);
    u8g2.clearBuffer(); 
    u8g2.setFont(u8g2_font_ncenB08_tr); 
    u8g2.drawStr((128 - u8g2.getStrWidth("OTA Update")) / 2, 12, "OTA Update");
    u8g2.setFont(u8g2_font_profont11_tf); 
    u8g2.drawStr((128 - u8g2.getStrWidth(text.c_str())) / 2, 32, text.c_str());
    if (progress >= 0) { 
        u8g2.drawFrame(14, 45, 100, 10); 
        u8g2.drawBox(14, 45, progress, 10); 
    }
    u8g2.sendBuffer();
}

void drawStatusIcons() {
    if (currentUIMode != UI_MODE_MAIN_SCREENS || currentPageIndex != SCREEN_TIME) return;
    int x = 0; 
    const int spacing = 10;
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
    Serial.println("DEBUG: updateScreens");
    NUM_SCREENS = isEngineeringMode ? 8 : 4;
    Serial.printf("DEBUG: Number of screens set to %d\n", NUM_SCREENS);
    if (currentUIMode == UI_MODE_MAIN_SCREENS) {
        rotaryEncoder.setBoundaries(0, NUM_SCREENS - 1, true);
        if (currentPageIndex >= NUM_SCREENS) { 
            currentPageIndex = SCREEN_TIME; 
            Serial.println("DEBUG: Resetting currentPageIndex to SCREEN_TIME");
        }
        rotaryEncoder.setEncoderValue(currentPageIndex);
    } else if (currentUIMode == UI_MODE_SYSTEM_MENU) {
        rotaryEncoder.setBoundaries(0, NUM_MENU_ITEMS - 1, true);
        rotaryEncoder.setEncoderValue(selectedMenuItem);
    }
    updateDisplay();
}

void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi) {
    // Serial.printf("DEBUG: drawChart_OriginalStyle - Title: %s\n", title);
    u8g2.setFont(u8g2_font_6x10_tf); 
    u8g2.drawStr(2, 8, title);
    if (historyCount < 2) { u8g2.setFont(u8g2_font_6x10_tf); u8g2.drawStr(10, 35, "No Data"); return; }
    loadHistoryWindow(historyViewOffset);
    int displayCount = min(HISTORY_WINDOW_SIZE, historyCount);
    if (displayCount < 2) { u8g2.drawStr(10, 35, "Insufficient Data"); return; }
    float minVal = 999, maxVal = -999;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        if (val < minVal) minVal = val; 
        if (val > maxVal) maxVal = val;
    }
    if (isRssi) {
        minVal = max(minVal, -100.0f); 
        maxVal = min(maxVal, -30.0f);
        if (maxVal - minVal < 10) { float mid = (minVal + maxVal) / 2; minVal = mid - 5; maxVal = mid + 5; }
    } else if (isTemp && maxVal - minVal < 1) { 
        float mid = (minVal + maxVal) / 2; minVal = mid - 0.5; maxVal = mid + 0.5; 
    } else if (!isTemp && maxVal - minVal < 2) { 
        float mid = (minVal + maxVal) / 2; minVal = mid - 1; maxVal = mid + 1; 
    }
    float range = maxVal - minVal; 
    if (range < 0.1) range = 1;
    int chartX = 18, chartY = 15, chartW = 128 - chartX - 2, chartH = 40;
    u8g2.setFont(u8g2_font_5x7_tr); 
    char buf[12];
    if (isRssi) { 
        sprintf(buf, "%d", (int)maxVal); 
        u8g2.drawStr(0, chartY + 5, buf); 
        sprintf(buf, "%d", (int)minVal); 
        u8g2.drawStr(0, chartY + chartH, buf); 
    } else { 
        sprintf(buf, isTemp ? "%.1f" : "%.0f", maxVal); 
        u8g2.drawStr(0, chartY + 5, buf); 
        sprintf(buf, isTemp ? "%.1f" : "%.0f", minVal); 
        u8g2.drawStr(0, chartY + chartH, buf); 
    }
    u8g2.drawFrame(chartX, chartY, chartW, chartH);
    int lastX = -1, lastY = -1;
    for (int i = 0; i < displayCount; i++) {
        float val = isRssi ? historyWindowBuffer[i].rssi : (isTemp ? historyWindowBuffer[i].temp : historyWindowBuffer[i].hum);
        int x = chartX + (i * chartW / displayCount); 
        int y = chartY + chartH - 1 - ((val - minVal) / range * (chartH - 2));
        if (lastX >= 0) u8g2.drawLine(lastX, lastY, x, y);
        lastX = x; 
        lastY = y;
    }
    char countStr[20]; 
    sprintf(countStr, "[%d/%d]", displayCount, historyCount); 
    u8g2.drawStr(128 - u8g2.getStrWidth(countStr) - 2, 10, countStr);
    char offsetStr[10];
    if (historyViewOffset == 0) { 
        strcpy(offsetStr, "Now"); 
    } else { 
        float hours = (historyViewOffset * historyRecordInterval) / 3600000.0; 
        sprintf(offsetStr, "-%.1fh", hours); 
    }
    u8g2.drawStr(128 - u8g2.getStrWidth(offsetStr) - 2, 64, offsetStr);
    if (currentEncoderMode == MODE_VIEW_ADJUST) { u8g2.drawStr(2, 64, "VIEW"); }
}

void drawTempChartScreen() { drawChart_OriginalStyle("Temp Chart", true, false); }
void drawHumChartScreen() { drawChart_OriginalStyle("Humid Chart", false, false); }
void drawRssiChartScreen() { drawChart_OriginalStyle("RSSI Chart", false, true); }

void drawTimeScreen() {
    time_t now; 
    time(&now);
    if (now < 1672531200) { 
        u8g2.setFont(u8g2_font_ncenB08_tr); 
        u8g2.drawStr(10, 32, "Time not set"); 
    } else { 
        struct tm * ptm = localtime(&now); 
        u8g2.setFont(u8g2_font_fub20_tn); 
        char s[9]; 
        sprintf(s, "%02d:%02d:%02d", ptm->tm_hour, ptm->tm_min, ptm->tm_sec); 
        u8g2.drawStr((128 - u8g2.getStrWidth(s)) / 2, 42, s); 
    }
}

void drawDateScreen() {
    time_t now; 
    time(&now);
    if (now < 1672531200) { 
        u8g2.setFont(u8g2_font_ncenB08_tr); 
        u8g2.drawStr(10, 32, "Time not set"); 
        return; 
    }
    struct tm * ptm = localtime(&now); 
    const char * week[] = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    const char * month[] = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    char day[4], year[6]; 
    sprintf(day, "%02d", ptm->tm_mday); 
    sprintf(year, "%d", ptm->tm_year + 1900);
    u8g2.setFont(u8g2_font_logisoso42_tn); 
    u8g2.drawStr(5, 50, day); 
    u8g2.drawVLine(64, 8, 48);
    u8g2.setFont(u8g2_font_helvB12_tr); 
    u8g2.drawStr(72, 22, week[ptm->tm_wday]); 
    u8g2.setFont(u8g2_font_ncenR10_tr);
    u8g2.drawStr(72, 40, month[ptm->tm_mon]); 
    u8g2.drawStr(72, 56, year);
}

void drawWeatherScreen() {
    u8g2.setFont(u8g2_font_ncenB10_tr); 
    u8g2.drawStr(0, 12, city.c_str());
    if (wifiState != WIFI_CONNECTED) { 
        u8g2.setFont(u8g2_font_ncenB08_tr); 
        u8g2.drawStr(0, 40, "No WiFi"); 
        return; 
    }
    if (weatherData.valid) {
        char buf[20]; 
        const char* icon = getWeatherIcon(weatherData.description);
        u8g2.setFont(u8g2_font_open_iconic_weather_4x_t); 
        u8g2.drawStr(5, 50, icon);
        u8g2.setFont(u8g2_font_fub25_tn); 
        sprintf(buf, "%.1f", weatherData.temp); 
        u8g2.drawStr(45, 32, buf);
        u8g2.drawCircle(45 + u8g2.getStrWidth(buf) + 4, 15, 3);
        u8g2.setFont(u8g2_font_unifont_t_chinese2); 
        u8g2.drawStr(45, 50, weatherData.description.c_str());
        u8g2.setFont(u8g2_font_ncenR10_tr); 
        sprintf(buf, "H:%d%%", weatherData.humidity); 
        u8g2.drawStr(45, 64, buf);
    } else { 
        u8g2.setFont(u8g2_font_ncenB08_tr); 
        u8g2.drawStr(0, 32, "Updating..."); 
    }
}

void drawSensorScreen() {
    if (!sensorDataValid) { 
        u8g2.setFont(u8g2_font_ncenB08_tr); 
        u8g2.drawStr(10, 40, "Sensor Init..."); 
        return; 
    }
    char buf[20];
    u8g2.setFont(u8g2_font_helvB10_tr); 
    u8g2.drawStr((128 - u8g2.getStrWidth("INDOOR")) / 2, 12, "INDOOR");
    u8g2.setFont(u8g2_font_ncenR10_tr); 
    u8g2.drawStr(10, 38, "TEMP");
    u8g2.setFont(u8g2_font_fub25_tn); 
    sprintf(buf, "%.1f C", cachedTemp); 
    u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 38, buf);
    u8g2.setFont(u8g2_font_ncenR10_tr); 
    u8g2.drawStr(10, 62, "HUMI");
    u8g2.setFont(u8g2_font_fub25_tn); 
    sprintf(buf, "%.0f %%", cachedHum); 
    u8g2.drawStr(128 - u8g2.getStrWidth(buf) - 10, 62, buf);
}

void drawSystemScreen() {
    Serial.println("DEBUG: drawSystemScreen");
    u8g2.setFont(u8g2_font_ncenB08_tr);
    u8g2.drawStr(0, 12, "System Info");
    u8g2.drawStr(128 - u8g2.getStrWidth(FIRMWARE_VERSION), 12, FIRMWARE_VERSION);
    u8g2.setFont(u8g2_font_profont11_tf);
    int y = 28;
    String ssid = WiFi.SSID();
    if (ssid == "") ssid = "N/A";
    if (wifiState == WIFI_CONNECTED) {
        u8g2.drawStr(0, y, ("SSID: " + ssid).c_str()); y += 12;
        u8g2.drawStr(0, y, ("RSSI: " + String(WiFi.RSSI()) + " dBm").c_str()); y += 12;
        u8g2.drawStr(0, y, ("IP: " + WiFi.localIP().toString()).c_str()); y += 12;
    } else {
        u8g2.drawStr(0, y, "WiFi Disconnected"); y += 12;
    }
    u8g2.drawStr(0, y, ("Heap: " + String(ESP.getFreeHeap() / 1024) + " KB").c_str()); y += 12;
    u8g2.drawStr(0, y, ("Up: " + String(millis() / 60000) + " min").c_str());
}

const char* getWeatherIcon(const String &desc) {
    String s = desc; 
    s.toLowerCase();
    if (s.indexOf("clear") >= 0 || s.indexOf("晴") >= 0) return "A";
    if (s.indexOf("cloud") >= 0 || s.indexOf("雲") >= 0 || s.indexOf("阴") >= 0) return "C";
    if (s.indexOf("rain") >= 0 || s.indexOf("雨") >= 0) return "R";
    if (s.indexOf("snow") >= 0 || s.indexOf("雪") >= 0) return "S";
    if (s.indexOf("thunder") >= 0 || s.indexOf("雷") >= 0) return "T";
    if (s.indexOf("fog") >= 0 || s.indexOf("霧") >= 0 || s.indexOf("霾") >= 0) return "M";
    return "C";
}
