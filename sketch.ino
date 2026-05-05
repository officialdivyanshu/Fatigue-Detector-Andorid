#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// I2C LCD address (most common: 0x27)
// Change to 0x3F only if this doesn't work
LiquidCrystal_I2C lcd(0x27, 16, 2);

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("ESP32 started");

  // Initialize I2C with ESP32 default pins
  Wire.begin(21, 22);   // SDA = GPIO21, SCL = GPIO22

  lcd.init();           // Initialize LCD
  lcd.backlight();      // Turn on backlight

  lcd.setCursor(0, 0);
  lcd.print("ESP32 I2C LCD");

  lcd.setCursor(0, 1);
  lcd.print("Working OK");

  Serial.println("LCD initialized");
}

void loop() {
  // Nothing needed here for test
}
