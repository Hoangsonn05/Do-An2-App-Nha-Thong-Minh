#pragma once

#include <Arduino.h>
#include <MFRC522.h>
#include <SPI.h>
#include <Wire.h>

class LongSensors {
 public:
  // Provisional ADC thresholds for a sensor whose output drops when wet.
  static constexpr int rainWetAdc = 2500;
  static constexpr int rainDryAdc = 3000;
  static_assert(rainWetAdc >= 0 && rainWetAdc < rainDryAdc && rainDryAdc <= 4095);

  void begin() {
    pinMode(reedPin, INPUT);  // GPIO36 requires an external pull-up.
    reed = reedCandidate = digitalRead(reedPin) == LOW;
    Wire.begin(32, 33, 100000);
    Wire.setTimeOut(10);
    initLcd();

    pinMode(rfidSs, OUTPUT);
    digitalWrite(rfidSs, HIGH);
    SPI.begin(22, 16, 13, rfidSs);
    rfid.PCD_Init();
    const byte version = rfid.PCD_ReadRegister(MFRC522::VersionReg);
    rfidReady = version != 0 && version != 0xFF;
  }

  void tick(float temperature, float humidity, uint32_t lastGoodDht,
            bool raining, bool smokeAlert) {
    const uint32_t now = millis();
    const bool closed = digitalRead(reedPin) == LOW;
    if (closed != reedCandidate) {
      reedCandidate = closed;
      reedSince = now;
    }
    if (reed != reedCandidate && now - reedSince >= 80) {
      reed = reedCandidate;
      reedChanged = true;
    }

    sampleLight(now);
    readCard(now);
    if (!lcdAddress || now - lastLcd < 2000) return;
    lastLcd = now;
    if (!present(lcdAddress)) {
      lcdAddress = 0;
      return;
    }
    char line[32];
    if (isnan(temperature) || isnan(humidity) || now - lastGoodDht >= 10000) {
      lcdLine(0, "DHT: chua co");
    } else {
      snprintf(line, sizeof(line), "T:%.1fC H:%.0f%%", temperature, humidity);
      lcdLine(0, line);
    }
    if (smokeAlert) lcdLine(1, "CANH BAO MQ-2");
    else if (raining) lcdLine(1, "DANG MUA");
    else if (!isnan(lux)) {
      snprintf(line, sizeof(line), "Anh sang %.0f lx", lux);
      lcdLine(1, line);
    } else lcdLine(1, reed ? "REED: DONG" : "REED: MO");
  }

  bool reedClosed() const { return reed; }

  bool takeReedChange() {
    const bool changed = reedChanged;
    reedChanged = false;
    return changed;
  }

  bool takeAllowedCard() {
    const bool allowed = cardAllowed;
    cardAllowed = false;
    return allowed;
  }

  String telemetry(int rainRaw) const {
    return String(",\"light_lux\":") + (isnan(lux) ? String("null") : String(lux, 1)) +
           ",\"reed_closed\":" + String(reed ? 1 : 0) +
           ",\"rain_raw\":" + String(rainRaw) +
           ",\"rfid_ready\":" + String(rfidReady ? 1 : 0) +
           ",\"lcd_ready\":" + String(lcdAddress ? 1 : 0);
  }

 private:
  static constexpr int reedPin = 36, rfidSs = 5, rfidReset = 17;
  static constexpr uint8_t bh1750Address = 0x23;  // Use 0x5C when ADDR is high.
  // Use an uppercase hexadecimal UID without spaces, for a model door only.
  // Leave empty to print UIDs locally without opening the door.
  static constexpr char allowedUid[] = "899719E6";

  MFRC522 rfid{rfidSs, rfidReset};
  bool reed = false, reedCandidate = false, reedChanged = false;
  bool rfidReady = false, cardAllowed = false, lightPending = false;
  uint8_t lcdAddress = 0;
  float lux = NAN;
  uint32_t reedSince = 0, lastRfid = 0, lastLcd = 0;
  uint32_t lightStarted = 0, nextLight = 0;

  void readCard(uint32_t now) {
    if (!rfidReady || now - lastRfid < 150) return;
    lastRfid = now;
    if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) return;

    char uid[21] = {};
    for (byte i = 0; i < rfid.uid.size && i < 10; ++i) {
      snprintf(uid + 2 * i, sizeof(uid) - 2 * i, "%02X", rfid.uid.uidByte[i]);
    }
    Serial.printf("RFID UID: %s\n", uid);
    cardAllowed = allowedUid[0] && strcmp(uid, allowedUid) == 0;
    rfid.PICC_HaltA();
    rfid.PCD_StopCrypto1();
  }

  void sampleLight(uint32_t now) {
    if (lightPending) {
      if (now - lightStarted < 180) return;
      lightPending = false;
      if (Wire.requestFrom(bh1750Address, size_t{2}) == 2) {
        const int high = Wire.read();
        const int low = Wire.read();
        lux = ((high << 8) | low) / 1.2f;
      } else lux = NAN;
    }
    if (static_cast<int32_t>(now - nextLight) < 0) return;
    nextLight = now + 1000;
    Wire.beginTransmission(bh1750Address);
    Wire.write(0x20);
    lightPending = Wire.endTransmission() == 0;
    lightStarted = now;
    if (!lightPending) lux = NAN;
  }

  bool present(uint8_t address) {
    Wire.beginTransmission(address);
    return Wire.endTransmission() == 0;
  }

  void initLcd() {
    for (uint8_t address : {0x27, 0x3F}) {
      if (present(address)) {
        lcdAddress = address;
        break;
      }
    }
    if (!lcdAddress) return;
    delay(50);
    lcdNibble(0x30);
    delay(5);
    lcdNibble(0x30);
    delayMicroseconds(150);
    lcdNibble(0x30);
    lcdNibble(0x20);
    lcdByte(0x28);
    lcdByte(0x08);
    lcdByte(0x01);
    delay(2);
    lcdByte(0x06);
    lcdByte(0x0C);
  }

  // Common PCF8574 mapping: P0=RS, P1=RW, P2=E, P3=backlight, P4-P7=D4-D7.
  void lcdNibble(uint8_t upperBits, bool data = false) {
    const uint8_t output = (upperBits & 0xF0) | 0x08 | (data ? 0x01 : 0);
    Wire.beginTransmission(lcdAddress);
    Wire.write(output);
    Wire.write(output | 0x04);
    Wire.write(output);
    if (Wire.endTransmission() != 0) lcdAddress = 0;
    delayMicroseconds(50);
  }

  void lcdByte(uint8_t value, bool data = false) {
    if (lcdAddress) lcdNibble(value, data);
    if (lcdAddress) lcdNibble(value << 4, data);
  }

  void lcdLine(uint8_t line, const char* text) {
    lcdByte(line ? 0xC0 : 0x80);
    char padded[17];
    snprintf(padded, sizeof(padded), "%-16.16s", text);
    for (size_t i = 0; i < 16 && lcdAddress; ++i) lcdByte(padded[i], true);
  }
};
