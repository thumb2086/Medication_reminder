
#pragma once

#include <Arduino.h>

void setupOTA();
void enterOtaMode();
void handleWiFiConnection();
void startWiFiConnection();
void syncTimeNTPForce();
void fetchWeatherData();
