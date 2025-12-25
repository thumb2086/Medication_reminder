
#include "globals.h"

// Pre-declare functions from other modules that are used here
void playTickSound();
void playConfirmSound();
void updateScreens();
void updateDisplay();
void enterOtaMode();
void returnToMainScreen();
void startWiFiConnection();

void handleEncoder() {
    if (isAlarmRinging) return; // Prevent interaction during alarm
    if (rotaryEncoder.encoderChanged()) {
        playTickSound();
        if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            selectedMenuItem = (SystemMenuItem)rotaryEncoder.readEncoder();
            if (selectedMenuItem >= menuViewOffset + MAX_MENU_ITEMS_ON_SCREEN) { menuViewOffset = selectedMenuItem - MAX_MENU_ITEMS_ON_SCREEN + 1; }
            if (selectedMenuItem < menuViewOffset) { menuViewOffset = selectedMenuItem; }
        } else if (currentEncoderMode == MODE_VIEW_ADJUST) {
            historyViewOffset = rotaryEncoder.readEncoder();
        } else {
            currentPageIndex = (ScreenState)rotaryEncoder.readEncoder();
        }
        updateDisplay();
    }
}

void handleEncoderPush() {
    if (isAlarmRinging) return; // Prevent interaction during alarm
    if (digitalRead(ENCODER_PSH_PIN) == LOW && (millis() - lastEncoderPushTime > 300)) {
        lastEncoderPushTime = millis(); playConfirmSound();
        switch (currentUIMode) {
            case UI_MODE_MAIN_SCREENS:
                if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) { 
                    currentUIMode = UI_MODE_SYSTEM_MENU; 
                    selectedMenuItem = MENU_ITEM_WIFI; 
                    menuViewOffset = 0; 
                    updateScreens(); 
                }
                else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) { 
                    currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION; 
                }
                break;
            case UI_MODE_SYSTEM_MENU:
                switch (selectedMenuItem) {
                    case MENU_ITEM_WIFI: 
                        u8g2.clearBuffer(); 
                        u8g2.setFont(u8g2_font_ncenB10_tr); 
                        u8g2.drawStr((128-u8g2.getStrWidth("Starting WiFi..."))/2,38,"Starting WiFi..."); 
                        u8g2.sendBuffer(); 
                        delay(1000); 
                        startWiFiConnection(); 
                        returnToMainScreen(); 
                        break;
                    case MENU_ITEM_OTA: enterOtaMode(); break;
                    case MENU_ITEM_INFO: currentUIMode = UI_MODE_INFO_SCREEN; break;
                    case MENU_ITEM_REBOOT: 
                        u8g2.clearBuffer(); 
                        u8g2.setFont(u8g2_font_ncenB10_tr); 
                        u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2,38,"Rebooting..."); 
                        u8g2.sendBuffer(); 
                        delay(1000); 
                        ESP.restart(); 
                        break;
                    case MENU_ITEM_BACK: returnToMainScreen(); break;
                }
                break;
            case UI_MODE_INFO_SCREEN: currentUIMode = UI_MODE_SYSTEM_MENU; break;
        }
        updateDisplay();
    }
}

void handleButtons() {
    bool isPressed = (digitalRead(BUTTON_CONFIRM_PIN) == LOW);
    if (isPressed && !confirmButtonPressed) { 
        confirmPressStartTime = millis(); 
        confirmButtonPressed = true; 
    }
    else if (!isPressed && confirmButtonPressed) {
        if (millis() - confirmPressStartTime < 3000) { 
            lastConfirmPressTime = millis(); 
            playConfirmSound(); 
            returnToMainScreen(); 
        }
        confirmButtonPressed = false;
    }
    if (confirmButtonPressed && (millis() - confirmPressStartTime >= 3000)) { 
        playConfirmSound(); 
        enterOtaMode(); 
        confirmButtonPressed = false; 
    }
}

void handleBackButton() {
    if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 300)) {
        lastBackPressTime = millis();
        playConfirmSound();
        if (isAlarmRinging) {
            isAlarmRinging = false;
            pixels.clear();
            pixels.show();
            Serial.println("Alarm Stopped by user.");
            return;
        }
        if (currentUIMode == UI_MODE_SYSTEM_MENU || currentUIMode == UI_MODE_INFO_SCREEN) {
            returnToMainScreen();
        } else if (currentUIMode == UI_MODE_MAIN_SCREENS && currentEncoderMode == MODE_VIEW_ADJUST) {
            currentEncoderMode = MODE_NAVIGATION;
        } else {
            currentPageIndex = SCREEN_TIME;
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
        }
        updateDisplay();
    }
}
