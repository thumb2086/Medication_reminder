
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
        Serial.println("DEBUG: handleEncoder - Encoder changed");
        playTickSound();
        if (currentUIMode == UI_MODE_SYSTEM_MENU) {
            selectedMenuItem = (SystemMenuItem)rotaryEncoder.readEncoder();
            Serial.printf("DEBUG: System menu navigation, selectedMenuItem: %d\n", selectedMenuItem);
            if (selectedMenuItem >= menuViewOffset + MAX_MENU_ITEMS_ON_SCREEN) { menuViewOffset = selectedMenuItem - MAX_MENU_ITEMS_ON_SCREEN + 1; }
            if (selectedMenuItem < menuViewOffset) { menuViewOffset = selectedMenuItem; }
        } else if (currentEncoderMode == MODE_VIEW_ADJUST) {
            historyViewOffset = rotaryEncoder.readEncoder();
            Serial.printf("DEBUG: History view adjust, offset: %d\n", historyViewOffset);
        } else {
            currentPageIndex = (ScreenState)rotaryEncoder.readEncoder();
            Serial.printf("DEBUG: Main screen navigation, currentPageIndex: %d\n", currentPageIndex);
        }
        updateDisplay();
    }
}

void handleEncoderPush() {
    if (isAlarmRinging) return; // Prevent interaction during alarm
    if (digitalRead(ENCODER_PSH_PIN) == LOW && (millis() - lastEncoderPushTime > 300)) {
        lastEncoderPushTime = millis(); 
        playConfirmSound();
        Serial.println("DEBUG: handleEncoderPush - Encoder pushed");
        switch (currentUIMode) {
            case UI_MODE_MAIN_SCREENS:
                Serial.println("DEBUG: Encoder push in UI_MODE_MAIN_SCREENS");
                if (isEngineeringMode && currentPageIndex == SCREEN_SYSTEM) { 
                    Serial.println("DEBUG: Entering system menu.");
                    currentUIMode = UI_MODE_SYSTEM_MENU; 
                    selectedMenuItem = MENU_ITEM_WIFI; 
                    menuViewOffset = 0; 
                    updateScreens(); 
                }
                else if (isEngineeringMode && (currentPageIndex >= SCREEN_TEMP_CHART && currentPageIndex < SCREEN_SYSTEM)) { 
                    currentEncoderMode = (currentEncoderMode == MODE_NAVIGATION) ? MODE_VIEW_ADJUST : MODE_NAVIGATION; 
                    Serial.printf("DEBUG: Toggling chart view mode to %s\n", (currentEncoderMode == MODE_NAVIGATION) ? "NAVIGATION" : "VIEW_ADJUST");
                }
                break;
            case UI_MODE_SYSTEM_MENU:
                Serial.printf("DEBUG: Encoder push in UI_MODE_SYSTEM_MENU, item %d\n", selectedMenuItem);
                switch (selectedMenuItem) {
                    case MENU_ITEM_WIFI: 
                        Serial.println("DEBUG: Menu action: Starting WiFi...");
                        u8g2.clearBuffer(); 
                        u8g2.setFont(u8g2_font_ncenB10_tr); 
                        u8g2.drawStr((128-u8g2.getStrWidth("Starting WiFi..."))/2,38,"Starting WiFi..."); 
                        u8g2.sendBuffer(); 
                        delay(1000); 
                        startWiFiConnection(); 
                        returnToMainScreen(); 
                        break;
                    case MENU_ITEM_OTA: 
                        Serial.println("DEBUG: Menu action: Entering OTA mode.");
                        enterOtaMode(); 
                        break;
                    case MENU_ITEM_INFO: 
                        Serial.println("DEBUG: Menu action: Entering info screen.");
                        currentUIMode = UI_MODE_INFO_SCREEN; 
                        break;
                    case MENU_ITEM_REBOOT: 
                        Serial.println("DEBUG: Menu action: Rebooting...");
                        u8g2.clearBuffer(); 
                        u8g2.setFont(u8g2_font_ncenB10_tr); 
                        u8g2.drawStr((128-u8g2.getStrWidth("Rebooting..."))/2,38,"Rebooting..."); 
                        u8g2.sendBuffer(); 
                        delay(1000); 
                        ESP.restart(); 
                        break;
                    case MENU_ITEM_BACK: 
                        Serial.println("DEBUG: Menu action: Returning to main screen.");
                        returnToMainScreen(); 
                        break;
                }
                break;
            case UI_MODE_INFO_SCREEN: 
                Serial.println("DEBUG: Encoder push in UI_MODE_INFO_SCREEN, returning to menu.");
                currentUIMode = UI_MODE_SYSTEM_MENU; 
                break;
        }
        updateDisplay();
    }
}

void handleButtons() {
    bool isPressed = (digitalRead(BUTTON_CONFIRM_PIN) == LOW);
    if (isPressed && !confirmButtonPressed) { 
        confirmPressStartTime = millis(); 
        confirmButtonPressed = true; 
        Serial.println("DEBUG: handleButtons - Confirm button pressed down.");
    }
    else if (!isPressed && confirmButtonPressed) {
        if (millis() - confirmPressStartTime < 3000) { 
            Serial.println("DEBUG: Confirm button short press detected.");
            lastConfirmPressTime = millis(); 
            playConfirmSound(); 
            returnToMainScreen(); 
        }
        confirmButtonPressed = false;
    }
    if (confirmButtonPressed && (millis() - confirmPressStartTime >= 3000)) { 
        Serial.println("DEBUG: Confirm button long press detected, entering OTA mode.");
        playConfirmSound(); 
        enterOtaMode(); 
        confirmButtonPressed = false; 
    }
}

void handleBackButton() {
    if (digitalRead(BUTTON_BACK_PIN) == LOW && (millis() - lastBackPressTime > 300)) {
        lastBackPressTime = millis();
        playConfirmSound();
        Serial.println("DEBUG: handleBackButton - Back button pressed.");
        if (isAlarmRinging) {
            isAlarmRinging = false;
            pixels.clear();
            pixels.show();
            Serial.println("Alarm Stopped by user.");
            return;
        }
        if (currentUIMode == UI_MODE_SYSTEM_MENU || currentUIMode == UI_MODE_INFO_SCREEN) {
            Serial.println("DEBUG: Back button: returning to main screen from menu/info.");
            returnToMainScreen();
        } else if (currentUIMode == UI_MODE_MAIN_SCREENS && currentEncoderMode == MODE_VIEW_ADJUST) {
            Serial.println("DEBUG: Back button: exiting chart view adjust mode.");
            currentEncoderMode = MODE_NAVIGATION;
        } else {
            Serial.println("DEBUG: Back button: returning to time screen.");
            currentPageIndex = SCREEN_TIME;
            rotaryEncoder.setEncoderValue(SCREEN_TIME);
        }
        updateDisplay();
    }
}
