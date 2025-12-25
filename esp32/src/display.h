
#pragma once

#include <Arduino.h>

void updateDisplay();
void drawStatusIcons();
void drawChart_OriginalStyle(const char* title, bool isTemp, bool isRssi);
void drawTimeScreen();
void drawDateScreen();
void drawWeatherScreen();
void drawSensorScreen();
void drawTempChartScreen();
void drawHumChartScreen();
void drawRssiChartScreen();
void drawSystemScreen();
void drawSystemMenu();
void drawOtaScreen(String text, int progress = -1);
void updateScreens();
const char* getWeatherIcon(const String &desc);
