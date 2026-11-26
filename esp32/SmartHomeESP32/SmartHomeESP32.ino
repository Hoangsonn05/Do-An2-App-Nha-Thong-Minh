/*
 * SMART HOME ESP32 - TOAN BO CHUONG TRINH TRONG MOT FILE
 * Arduino IDE: ESP32 Dev Module.
 * Thu vien: DHT sensor library, Adafruit Unified Sensor, ESP32Servo.
 *
 * Bo sung:
 * - HOME: dieu khien binh thuong, PIR bat coi 3 giay.
 * - AWAY: tat den, dong cua va giam sat ngay khi vung cua an toan.
 * - Khi giam sat: PIR bat coi 10 giay. Sieu am chi kiem tra vung cua.
 * - HOME: cua dang mo, vung cua trong 5 giay thi tu dong dong.
 *
 * Servo dung nguon 5V ngoai, GND chung.
 * ECHO SRF05 phai qua cau phan ap.
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h>
#include <ESP32Servo.h>
#include <Preferences.h>
#include <time.h>
#include <sys/time.h>

// ===== BEGIN CONFIG =====

// Copy wifi_config.example.h to wifi_config.h for local Wi-Fi credentials.
// With no local file, configure Wi-Fi through SmartHome-Setup.
#if __has_include("wifi_config.h")
#include "wifi_config.h"
#else
const char* WIFI_SSID = "";
const char* WIFI_PASS = "";
#endif

// De trong de ket noi bang IP trong cung LAN.
const char* DEVICE_KEY = "";

constexpr int DHT_PIN = 4;
constexpr int PIR_PIN = 32;
constexpr int OBSTACLE_PIN = 27;

constexpr int TRIG_PIN = 16;
constexpr int ECHO_PIN = 17;
constexpr int RAIN_PIN = 33;

constexpr int SERVO_PIN = 18;
constexpr int LIGHT_PIN = 25;
constexpr int ALARM_PIN = 26;

constexpr int CLOTHES_SERVO_PIN = 19;
constexpr int BUZZER_PIN = 14;

// true: coi thu dong; false: coi chu dong.
constexpr bool BUZZER_PASSIVE = true;
constexpr uint32_t BUZZER_FREQUENCY = 2000;
constexpr uint8_t BUZZER_PWM_CHANNEL = 14;

constexpr int CLOTHES_RETRACTED_ANGLE = 0;
constexpr int CLOTHES_EXTENDED_ANGLE = 90;

constexpr bool OBSTACLE_ACTIVE_LOW = true;
constexpr bool RAIN_ACTIVE_LOW = true;

constexpr int SERVO_CLOSED_ANGLE = 0;
constexpr int SERVO_OPEN_ANGLE = 90;

constexpr float DOOR_CLEARANCE_CM = 10.0f;

// Nguong sieu am bat coi va LED vang.
constexpr float ULTRASONIC_ALARM_CM = 10.0f;

// Tu dong dong cua sau 5 giay khong co vat can.
constexpr uint32_t DOOR_AUTO_CLOSE_MS = 5000;

// ECHO--1k--GPIO17--2k--GND.
// LED noi tiep dien tro 220-330 ohm.
// Servo dung nguon 5V ngoai va GND chung.

// ===== END CONFIG =====


// ===== BEGIN CONTROL LOGIC =====

inline bool ultrasonicObjectDetected(float distanceCm) {
  return distanceCm >= 2.0f &&
         distanceCm <= ULTRASONIC_ALARM_CM;
}

inline bool alarmSignal(bool alarm, bool incident, uint32_t now) {
  return alarm && (!incident || (now / 250) % 2 == 0);
}

class ClotheslineController {
public:
  bool retracted = true;
  bool autoRain = true;

  bool setRetracted(bool value, bool raining) {
    if (!value && autoRain && raining) return false;

    retracted = value;
    return true;
  }

  bool tick(bool raining) {
    if (autoRain && raining && !retracted) {
      retracted = true;
      return true;
    }

    return false;
  }
};

class StableInput {
public:
  void begin(bool initial, uint32_t now) {
    value = initial;
    candidate = initial;
    changed = now;
  }

  bool update(bool raw, uint32_t now) {
    if (raw != candidate) {
      candidate = raw;
      changed = now;
    }

    if (candidate != value && now - changed >= 50) {
      value = candidate;
      return true;
    }

    return false;
  }

  bool value = false;

private:
  bool candidate = false;
  uint32_t changed = 0;
};

inline bool doorCloseBlocked(
    bool doorOpen,
    bool obstacle,
    float distanceCm,
    float clearance) {

  return doorOpen &&
         (obstacle ||
          (distanceCm >= 2.0f && distanceCm <= clearance));
}

class HomeController {
public:
  enum Mode { HOME=0, AWAY=1 };
  enum Phase { DISARMED, EXIT_DELAY, ARMED, ENTRY_DELAY, ALARM, NIGHT };
  Mode mode=HOME;
  Phase phase=DISARMED;
  bool light=false, door=false, alarm=false;
  static constexpr uint32_t HOME_BUZZER_MS = 3000;
  static constexpr uint32_t AWAY_BUZZER_MS = 10000;
  bool nightLight=false, doorReminder=true, overdue=false, exitBlocked=false;
  uint32_t exitSeconds=0, entrySeconds=0, holdSeconds=30, incident=0;
  const char* lightReason="manual";
  const char* alarmReason="none";
  void (*onEvent)(const char*)=nullptr;

  const char* phaseName() const {
    switch(phase) {
      case EXIT_DELAY:return "EXIT_DELAY";
      case ARMED:return "ARMED";
      case ALARM:return "ALARM";
      default:return "DISARMED";
    }
  }
  const char* modeName() const { return mode == AWAY ? "AWAY" : "HOME"; }
  bool protecting() const { return mode == AWAY && phase != EXIT_DELAY; }
  uint32_t remaining(uint32_t) const { return 0; }

  void setMode(Mode value, bool closeBlocked) {
    const bool resolving = incident != 0;
    alarm = false;
    alarmReason = "none";
    mode = value;
    phase = value == HOME ? DISARMED : EXIT_DELAY;
    exitBlocked = false;
    if (resolving) emit("acknowledged");
    incident = 0;
    emit(value == AWAY ? "away_selected" : "home_selected");
    if (value == AWAY) finishArming(closeBlocked);
  }

  bool manual(const char* device,bool on,uint32_t now) {
    if(mode == AWAY) return false;
    if(same(device,"light")) {light=on;lightReason="manual";emit(on ? "light_manual_on":"light_manual_off");}
    else if(same(device,"door")) {
      if(on && !door) doorAt=now;
      door=on;if(!on) overdue=false;
      emit(on ? "servo_open":"servo_close");
    }
    else if(same(device,"alarm")) {
      alarm=on;alarmAt=now;alarmReason=on ? "manual":"none";
      emit(on ? "alarm_manual_on":"alarm_manual_off");
    }
    else return false;
    return true;
  }
  void tick(uint32_t now, bool motion, bool closeBlocked) {
    const bool motionStarted = motion && !previousMotion;
    previousMotion = motion;
    if (phase == EXIT_DELAY) finishArming(closeBlocked);

    const uint32_t limit = protecting() ? AWAY_BUZZER_MS : HOME_BUZZER_MS;
    if (alarm && now - alarmAt >= limit) {
      alarm = false;
      alarmReason = "none";
      emit("siren_stopped");
    }
    if (motionStarted) {
      if (mode == HOME) {
        emit("motion_home");
        if (!alarm) startAlarm(now, "motion");
      } else if (protecting() && !alarm) {
        incident = ++incidentCounter;
        phase = ALARM;
        startAlarm(now, "motion");
        emit("motion_alarm");
      } else if (!protecting()) {
        emit("motion_waiting");
      }
    }
    bool isOverdue = doorReminder && door && (overdue || now - doorAt >= 120000);
    if (isOverdue && !overdue) { overdue = true; emit("servo_overdue"); }
    if (!isOverdue) overdue = false;
  }
private:
  uint32_t alarmAt=0, doorAt=0, incidentCounter=0;
  bool previousMotion = false;

  void startAlarm(uint32_t now, const char* reason) {
    alarm = true;
    alarmAt = now;
    alarmReason = reason;
  }

  void finishArming(bool closeBlocked) {
    light = false;
    lightReason = "mode";
    if (closeBlocked) {
      if (!exitBlocked) emit("exit_waiting");
      exitBlocked = true;
      return;
    }
    const bool wasOpen = door;
    door = false;
    overdue = false;
    exitBlocked = false;
    phase = ARMED;
    if (wasOpen) emit("servo_close");
    emit("armed");
  }
  static bool same(const char* a,const char* b) {while(*a && *a==*b) {a++;b++;}return *a==*b;}
  void emit(const char* kind) {if(onEvent) onEvent(kind);}
};

// ===== END CONTROL LOGIC =====


// ===== BEGIN MQ2 VENTILATION =====
// MQ-2 (GPIO34, ADC1) + servo cua so (GPIO21) + quat (GPIO22).
// AO 5V -> cau phan ap 10k (tren) / 20k (duoi) -> GPIO34.
// Servo cua so dung nguon 5V ngoai, GND chung.
constexpr int MQ2_PIN = 34;
constexpr int WINDOW_SERVO_PIN = 21;
constexpr int FAN_PIN = 22;
constexpr bool FAN_ACTIVE_HIGH = true;

constexpr int WINDOW_CLOSED_ANGLE = 0;
constexpr int WINDOW_OPEN_ANGLE = 90;

constexpr uint32_t MQ2_PREHEAT_MS = 60000;
constexpr uint32_t MQ2_SAMPLE_MS = 200;
constexpr float MQ2_VCC = 5.0f;
constexpr float MQ2_RL_KOHM = 5.0f;
// R0 PHAI hieu chuan ngoai troi sach (Rs trong khong khi sach / ~9.8).
constexpr float MQ2_R0_KOHM = 10.0f;
// UOC LUONG tu datasheet (ppm = A * (Rs/R0)^B), can hieu chinh theo module.
constexpr float MQ2_CURVE_A = 3616.1f;
constexpr float MQ2_CURVE_B = -2.675f;

constexpr int SMOKE_DEFAULT_PPM = 300;
constexpr int SMOKE_MIN_PPM = 50;
constexpr int SMOKE_MAX_PPM = 5000;
constexpr int SMOKE_HYST_PPM = 50;
constexpr int SMOKE_CONFIRM_SAMPLES = 3;
constexpr uint32_t SMOKE_CLEAR_MS = 5000;

// Logic thuan, khong cham phan cung. update() goi moi mau; tra ve true khi co canh.
class SmokeVentController {
public:
  bool autoEnabled = true;
  int thresholdPpm = SMOKE_DEFAULT_PPM;
  bool alert = false;
  bool justDetected = false;
  bool justCleared = false;

  bool update(int ppm, bool preheatDone, uint32_t now) {
    justDetected = false;
    justCleared = false;

    if (!preheatDone) {
      alert = false;
      confirm = 0;
      clearing = false;
      return false;
    }

    if (ppm < 0) {  // mau loi: giu trang thai, khong dem them
      confirm = 0;
      clearing = false;
      return false;
    }

    if (!alert) {
      confirm = ppm >= thresholdPpm ? confirm + 1 : 0;
      if (confirm >= SMOKE_CONFIRM_SAMPLES) {
        alert = true;
        justDetected = true;
        confirm = 0;
        clearing = false;
      }
    } else {
      int clearBelow = thresholdPpm - SMOKE_HYST_PPM;
      if (clearBelow < 1) clearBelow = 1;
      if (ppm < clearBelow) {
        if (!clearing) {
          clearing = true;
          clearSince = now;
        } else if (now - clearSince >= SMOKE_CLEAR_MS) {
          alert = false;
          justCleared = true;
          clearing = false;
        }
      } else {
        clearing = false;
      }
    }

    return justDetected || justCleared;
  }

private:
  int confirm = 0;
  bool clearing = false;
  uint32_t clearSince = 0;
};

// kind[24] => toi da 23 ky tu.
#define VENT_KIND_FITS(s) static_assert(sizeof(s) <= 24, "kind > 23")
VENT_KIND_FITS("smoke_detected");
VENT_KIND_FITS("smoke_auto_vent");
VENT_KIND_FITS("smoke_cleared");
VENT_KIND_FITS("window_opened");
VENT_KIND_FITS("window_closed");
VENT_KIND_FITS("fan_on");
VENT_KIND_FITS("fan_off");
VENT_KIND_FITS("smoke_rule_changed");

// ===== END MQ2 VENTILATION =====


// ===== WIFI, CAM BIEN, API VA NHAT KY =====

WebServer server(80);

// Giu nguyen loai DHT trong code ban dau.
DHT dht(DHT_PIN, DHT22);

Servo servo;
Servo clothesServo;

ClotheslineController clothesline;
Preferences settings;
HomeController home;

struct Event {
  uint64_t seq;
  int64_t epoch;
  uint32_t uptime;
  uint32_t boot;
  uint32_t incident;
  char kind[24];
};

struct Journal {
  uint32_t magic;
  uint32_t generation;
  uint32_t boot;
  uint64_t next;
  Event events[64];
} journal;

String streamId;

float temperature = NAN;
float humidity = NAN;

uint32_t lastDht = 0;
uint32_t lastGoodDht = 0;

int lastServo = -1;
int lastClothesServo = -1;
int lastBuzzer = -1;

bool buzzerReady = false;
bool pirReady = false;
bool setupPortal = false;

StableInput obstacleInput;
StableInput rainInput;

float distanceCm = -1;

uint32_t lastRange = 0;
uint32_t wifiStartedAt = 0;
uint32_t rebootAt = 0;

// Bo dem tu dong dong cua.
uint32_t doorClearSince = 0;
bool doorClearTiming = false;

String savedSsid;
String savedPass;


void readExtraSensors(bool force = false) {
  obstacleInput.update(
      digitalRead(OBSTACLE_PIN) ==
          (OBSTACLE_ACTIVE_LOW ? LOW : HIGH),
      millis()
  );

  if (rainInput.update(
          digitalRead(RAIN_PIN) ==
              (RAIN_ACTIVE_LOW ? LOW : HIGH),
          millis())) {

    recordEvent(
        rainInput.value ? "rain_started" : "rain_cleared"
    );
  }

  if (force || millis() - lastRange >= 250) {
    lastRange = millis();

    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(2);

    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);

    digitalWrite(TRIG_PIN, LOW);

    unsigned long echo = pulseIn(ECHO_PIN, HIGH, 25000);
    float measured = echo / 58.0f;

    distanceCm =
        echo > 0 && measured >= 2 && measured <= 400 ?
        measured :
        -1;
  }
}


bool unsafeDoorZone() {
  const bool obstacle = obstacleInput.value ||
      digitalRead(OBSTACLE_PIN) == (OBSTACLE_ACTIVE_LOW ? LOW : HIGH);
  return home.door && (obstacle || distanceCm < 2.0f || distanceCm > 400.0f ||
      doorCloseBlocked(true, false, distanceCm, DOOR_CLEARANCE_CM));
}

bool blockedClose() {
  readExtraSensors(true);

  bool obstacle =
      digitalRead(OBSTACLE_PIN) ==
      (OBSTACLE_ACTIVE_LOW ? LOW : HIGH);

  if (!(home.door && (distanceCm<2.0f || distanceCm>400.0f)) && !doorCloseBlocked(
          home.door,
          obstacle,
          distanceCm,
          DOOR_CLEARANCE_CM)) {

    return false;
  }

  static bool logged = false;
  static uint32_t last = 0;

  if (!logged || millis() - last >= 5000) {
    recordEvent("servo_blocked");
    last = millis();
    logged = true;
  }

  server.send(
      423,
      "text/plain",
      "Object detected near servo door; clear it before closing"
  );

  return true;
}


// Cua dang mo:
// - Co vat can: huy bo dem.
// - Khong co vat can lien tuc 5 giay: dong cua.
// Khong dung delay(5000), cac luong khac van tiep tuc chay.
void autoCloseDoor() {
  if (!home.door || home.mode!=HomeController::HOME) {
    doorClearTiming = false;
    return;
  }

  bool obstacle =
      obstacleInput.value ||
      digitalRead(OBSTACLE_PIN) ==
          (OBSTACLE_ACTIVE_LOW ? LOW : HIGH);

  bool objectDetected =
      distanceCm < 2.0f || distanceCm > 400.0f ||
      ultrasonicObjectDetected(distanceCm) ||
      doorCloseBlocked(
          home.door,
          obstacle,
          distanceCm,
          DOOR_CLEARANCE_CM
      );

  if (objectDetected) {
    doorClearTiming = false;
    return;
  }

  uint32_t now = millis();

  if (!doorClearTiming) {
    doorClearSince = now;
    doorClearTiming = true;
    return;
  }

  if (now - doorClearSince >= DOOR_AUTO_CLOSE_MS) {
    if (home.manual("door", false, now)) {
      doorClearTiming = false;
    }
  }
}


void onWifiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  if (event != ARDUINO_EVENT_WIFI_STA_DISCONNECTED) return;

  const uint8_t reason = info.wifi_sta_disconnected.reason;

  Serial.printf("[WiFi] Disconnected, reason=%u. ", reason);

  switch (reason) {
    case WIFI_REASON_NO_AP_FOUND:
      Serial.println(
          "Network not found. Check SSID, 2.4 GHz Wi-Fi and signal."
      );
      break;

    case WIFI_REASON_AUTH_FAIL:
    case WIFI_REASON_HANDSHAKE_TIMEOUT:
    case WIFI_REASON_4WAY_HANDSHAKE_TIMEOUT:
      Serial.println(
          "Authentication failed/timed out. Check password and router security."
      );
      break;

    case WIFI_REASON_BEACON_TIMEOUT:
      Serial.println(
          "Router signal lost. Check signal and power supply."
      );
      break;

    default:
      Serial.println(
          "Check Wi-Fi settings; automatic reconnect is enabled."
      );
      break;
  }
}


void startSetupPortal() {
  if (setupPortal) return;

  setupPortal = true;

  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP("SmartHome-Setup", "smarthome123");

  Serial.println(
      "Wi-Fi setup: join SmartHome-Setup "
      "(password smarthome123), then open http://192.168.4.1"
  );
}


void handleHomePage() {
  if (!setupPortal) {
    server.send(
        200,
        "text/plain",
        "Smart Home ESP32 3.5 - DHT22 / FC51 / SR505 / SRF05 / "
        "rain / door servo / clothesline servo / LEDs. "
        "Use /sensor for data."
    );

    return;
  }

  server.send(
      200,
      "text/html; charset=utf-8",
      R"HTML(<!doctype html>
<html lang="vi">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Wi-Fi Smart Home</title>
<body style="font:16px sans-serif;max-width:440px;margin:32px auto;padding:16px">
<h2>Kết nối Wi-Fi cho ESP32</h2>
<form method="post" action="/wifi">
<p>
<label>Tên Wi-Fi<br>
<input name="ssid" maxlength="32" required style="width:100%;padding:10px">
</label>
</p>
<p>
<label>Mật khẩu Wi-Fi<br>
<input name="password" type="password" maxlength="63" style="width:100%;padding:10px">
</label>
</p>
<button style="padding:12px">Lưu và khởi động lại</button>
</form>
<p>
Sau khi lưu, đưa điện thoại về cùng Wi-Fi và nhập IP ESP32 trong app.
Không dùng khóa thiết bị ở cấu hình mặc định.
</p>
</body>
</html>)HTML"
  );
}


bool clockReady() {
  return time(nullptr) > 1700000000;
}


uint32_t warmupRemaining() {
  const uint32_t now = millis();

  return pirReady || now >= 6000 ?
      0 :
      (6000 - now + 999) / 1000;
}


bool authorized() {
  if (DEVICE_KEY[0] == '\0') return true;

  if (strlen(DEVICE_KEY) < 12 ||
      strcmp(DEVICE_KEY, "THAY_KHOA_RIENG_CUA_BAN") == 0) {

    server.send(
        503,
        "text/plain",
        "Configure DEVICE_KEY before using the device"
    );

    return false;
  }

  const String received = server.header("X-Device-Key");

  unsigned int difference =
      received.length() ^ strlen(DEVICE_KEY);

  for (size_t i = 0; i < strlen(DEVICE_KEY); i++) {
    difference |=
        (i < received.length() ? received[i] : 0) ^
        DEVICE_KEY[i];
  }

  if (difference) {
    server.send(401, "text/plain", "Invalid device key");
    return false;
  }

  return true;
}


void recordEvent(const char* kind) {
  Event event{};

  event.seq = journal.next++;

  event.epoch = clockReady() ?
      (int64_t)time(nullptr) * 1000 :
      0;

  event.uptime = millis();
  event.boot = journal.boot;
  event.incident = home.incident;

  strlcpy(event.kind, kind, sizeof(event.kind));

  journal.events[(event.seq - 1) % 64] = event;

  settings.putBytes("journal3", &journal, sizeof(journal));
}


void outputs() {
  digitalWrite(LIGHT_PIN, home.light ? HIGH : LOW);

  // HOME uses a steady buzzer; AWAY pulses the buzzer during an incident.
  bool alarmOutput =
      alarmSignal(
          home.alarm,
          home.phase == HomeController::ALARM,
          millis()
      );

  digitalWrite(ALARM_PIN, alarmOutput ? HIGH : LOW);

  if (buzzerReady && lastBuzzer != (int)alarmOutput) {
    if (BUZZER_PASSIVE) {
      ledcWrite(BUZZER_PIN, alarmOutput ? 128 : 0);
    }
    else {
      digitalWrite(BUZZER_PIN, alarmOutput ? HIGH : LOW);
    }

    lastBuzzer = alarmOutput;
  }

  int position =
      home.door ? SERVO_OPEN_ANGLE : SERVO_CLOSED_ANGLE;

  if (position != lastServo) {
    servo.write(position);
    lastServo = position;
  }

  int clothesPosition =
      clothesline.retracted ?
      CLOTHES_RETRACTED_ANGLE :
      CLOTHES_EXTENDED_ANGLE;

  if (clothesPosition != lastClothesServo) {
    clothesServo.write(clothesPosition);
    lastClothesServo = clothesPosition;
  }
}


void saveConfig() {
  settings.putBool("night", home.nightLight);
  settings.putBool("door", home.doorReminder);

  settings.putUInt("exit", home.exitSeconds);
  settings.putUInt("entry", home.entrySeconds);
  settings.putUInt("hold", home.holdSeconds);
}


// ===== BEGIN MQ2 VENTILATION RUNTIME =====

Servo windowServo;
bool windowOpen = false;
bool fanOn = false;
int smokePpm = -1;
SmokeVentController smoke;

int lastWindowServo = -1;
int lastFanOut = -1;

uint32_t lastSmokeSample = 0;
uint32_t lastSmokeValid = 0;

// Giong recordEvent nhung incident = 0; bo qua cung loai lap trong 1 giay.
void recordVentEvent(const char* kind) {
  static char lastKind[24] = "";
  static uint32_t lastAt = 0;
  const uint32_t now = millis();

  if (lastKind[0] && strcmp(lastKind, kind) == 0 && now - lastAt < 1000) return;

  strlcpy(lastKind, kind, sizeof(lastKind));
  lastAt = now;

  Event event{};
  event.seq = journal.next++;
  event.epoch = clockReady() ? (int64_t)time(nullptr) * 1000 : 0;
  event.uptime = now;
  event.boot = journal.boot;
  event.incident = 0;
  strlcpy(event.kind, kind, sizeof(event.kind));

  journal.events[(event.seq - 1) % 64] = event;
  settings.putBytes("journal3", &journal, sizeof(journal));
}

void ventOutputs() {
  const int windowPos = windowOpen ? WINDOW_OPEN_ANGLE : WINDOW_CLOSED_ANGLE;
  if (windowPos != lastWindowServo) {
    windowServo.write(windowPos);
    lastWindowServo = windowPos;
  }

  const int fanOut = fanOn ? 1 : 0;
  if (fanOut != lastFanOut) {
    digitalWrite(FAN_PIN, (fanOn == FAN_ACTIVE_HIGH) ? HIGH : LOW);
    lastFanOut = fanOut;
  }
}

void setWindowOpen(bool value) {
  if (windowOpen == value) return;
  windowOpen = value;
  recordVentEvent(value ? "window_opened" : "window_closed");
}

void setFanOn(bool value) {
  if (fanOn == value) return;
  fanOn = value;
  recordVentEvent(value ? "fan_on" : "fan_off");
}

// Doc MQ-2 khong chan: moi MQ2_SAMPLE_MS, trung binh 16 mau.
int readSmokePpm(uint32_t now) {
  if (now < MQ2_PREHEAT_MS) return -1;

  uint32_t sum = 0;
  for (int i = 0; i < 16; i++) sum += analogReadMilliVolts(MQ2_PIN);
  const float mv = sum / 16.0f;

  if (mv <= 0.0f || mv >= 3300.0f) return -2;  // mau loi

  const float vout = mv * 1.5f / 1000.0f;  // cau phan ap 10k/20k
  if (vout >= MQ2_VCC) return -2;

  const float rs = MQ2_RL_KOHM * (MQ2_VCC - vout) / vout;
  float ppm = MQ2_CURVE_A * powf(rs / MQ2_R0_KOHM, MQ2_CURVE_B);
  if (!(ppm >= 0.0f)) return -2;
  if (ppm > 10000.0f) ppm = 10000.0f;
  return (int)ppm;
}

void smokeVentTick() {
  const uint32_t now = millis();
  if (now - lastSmokeSample < MQ2_SAMPLE_MS) return;
  lastSmokeSample = now;

  const bool preheatDone = now >= MQ2_PREHEAT_MS;
  const int reading = readSmokePpm(now);

  if (reading >= 0) {
    smokePpm = reading;
    lastSmokeValid = now;
  } else if (!preheatDone || now - lastSmokeValid > 2000) {
    smokePpm = -1;
  }

  smoke.update(smokePpm, preheatDone, now);

  if (smoke.justDetected) {
    recordVentEvent("smoke_detected");
    if (smoke.autoEnabled) {
      windowOpen = true;
      fanOn = true;
      recordVentEvent("smoke_auto_vent");
      if (lastWindowServo != WINDOW_OPEN_ANGLE) recordVentEvent("window_opened");
      if (lastFanOut != 1) recordVentEvent("fan_on");
    }
  }

  if (smoke.justCleared) recordVentEvent("smoke_cleared");

  ventOutputs();
}

String ventJson() {
  return String(",\"has_mq2\":1,\"smoke_ppm\":") +
         (smokePpm >= 0 ? String(smokePpm) : String("null")) +
         ",\"smoke_alert\":" + String(smoke.alert ? 1 : 0) +
         ",\"has_window\":1,\"window_open\":" + String(windowOpen ? 1 : 0) +
         ",\"has_fan\":1,\"fan_on\":" + String(fanOn ? 1 : 0) +
         ",\"smoke_auto_enabled\":" + String(smoke.autoEnabled ? 1 : 0) +
         ",\"smoke_threshold_ppm\":" + String(smoke.thresholdPpm);
}

void handleVentToggle(bool isWindow) {
  if (!authorized()) return;

  const String value = server.arg("state");
  if (value != "0" && value != "1") {
    server.send(400, "text/plain", "Invalid state");
    return;
  }

  const bool on = value == "1";

  if (!on && smoke.alert && smoke.autoEnabled) {
    server.send(412, "text/plain", "Smoke ventilation prevents closing");
    return;
  }

  if (home.mode == HomeController::AWAY && !smoke.alert) {
    server.send(409, "text/plain", "Disarm protection first");
    return;
  }

  if (isWindow) setWindowOpen(on);
  else setFanOn(on);

  ventOutputs();
  server.send(200, "text/plain", "OK");
}

void handleSmokeConfig() {
  if (!authorized()) return;

  const String autoArg = server.arg("auto");
  const String thrArg = server.arg("threshold");

  bool digits = thrArg.length() > 0 && thrArg.length() <= 4;
  for (size_t i = 0; digits && i < thrArg.length(); i++) {
    if (thrArg[i] < '0' || thrArg[i] > '9') digits = false;
  }
  const int thr = digits ? thrArg.toInt() : -1;

  if ((autoArg != "0" && autoArg != "1") ||
      thr < SMOKE_MIN_PPM || thr > SMOKE_MAX_PPM) {
    server.send(400, "text/plain", "Invalid smoke config");
    return;
  }

  smoke.autoEnabled = autoArg == "1";
  smoke.thresholdPpm = thr;
  settings.putBool("smkAuto", smoke.autoEnabled);
  settings.putInt("smkThr", smoke.thresholdPpm);

  recordVentEvent("smoke_rule_changed");

  if (smoke.autoEnabled && smoke.alert) {
    setWindowOpen(true);
    setFanOn(true);
  }

  ventOutputs();
  server.send(200, "text/plain", "OK");
}

// ===== END MQ2 VENTILATION RUNTIME =====


void handleSensor() {
  if (!authorized()) return;

  bool valid =
      !isnan(temperature) &&
      !isnan(humidity) &&
      millis() - lastGoodDht < 10000;

  uint32_t closeElapsed=millis()-doorClearSince;
  uint32_t closeRemaining=closeElapsed<DOOR_AUTO_CLOSE_MS ? (DOOR_AUTO_CLOSE_MS-closeElapsed+999)/1000:0;
  String q=String(char(34));
  String liveRules=","+q+"security_profile"+q+":"+q+"demo-two-modes"+q;
  liveRules+=","+q+"dht_type"+q+":"+q+"DHT22"+q;
  liveRules+=","+q+"exit_blocked"+q+":"+String(home.exitBlocked ? 1:0);
  liveRules+=","+q+"alarm_active"+q+":"+String(home.alarm ? 1:0);
  liveRules+=","+q+"alarm_source"+q+":"+q+home.alarmReason+q;
  liveRules+=","+q+"proximity_alarm_enabled"+q+":"+String(0);
  liveRules+=","+q+"ultrasonic_alarm_cm"+q+":"+String(ULTRASONIC_ALARM_CM,1);
  liveRules+=","+q+"door_auto_close_seconds"+q+":"+String(DOOR_AUTO_CLOSE_MS/1000);
  liveRules+=","+q+"door_auto_close_remaining"+q+":"+(home.door && doorClearTiming && !home.protecting() ? String(closeRemaining):String("null"));

  String extra =
      ",\"hardware_profile\":\"dht11-leds\",\"has_buzzer\":" +
      String(buzzerReady ? 1 : 0) +
      ",\"auth_required\":" +
      String(DEVICE_KEY[0] ? 1 : 0) +
      ",\"has_clothesline\":1,\"clothes_retracted\":" +
      String(clothesline.retracted ? 1 : 0) +
      ",\"rain_retract_enabled\":" +
      String(clothesline.autoRain ? 1 : 0) +
      ",\"obstacle\":" +
      String(obstacleInput.value ? 1 : 0) +
      ",\"raining\":" +
      String(rainInput.value ? 1 : 0) +
      ",\"distance_cm\":" +
      (distanceCm >= 0 ?
          String(distanceCm, 1) :
          String("null"));

  String json =
      "{\"firmware\":3,\"stream_id\":\"" + streamId +
      "\",\"temp\":" +
      (valid ? String((int)round(temperature)) : "null") +
      ",\"hum\":" +
      (valid ? String((int)round(humidity)) : "null") +
      ",\"motion\":" +
      String(digitalRead(PIR_PIN) == HIGH ? 1 : 0) +
      ",\"light\":" + String(home.light ? 1 : 0) +
      ",\"door\":" + String(home.door ? 1 : 0) +
      ",\"alarm\":" + String(home.alarm ? 1 : 0) +
      ",\"night_light\":" + String(home.nightLight ? 1 : 0) +
      ",\"door_reminder\":" + String(home.doorReminder ? 1 : 0) +
      ",\"door_overdue\":" + String(home.overdue ? 1 : 0) +
      ",\"clock_ready\":" + String(clockReady() ? 1 : 0) +
      ",\"mode\":\"" + home.modeName() +
      "\",\"phase\":\"" + home.phaseName() +
      "\",\"remaining\":" + String(home.remaining(millis())) +
      ",\"boot\":" + String(journal.boot) +
      ",\"incident\":" + String(home.incident) +
      ",\"exit_seconds\":" + String(home.exitSeconds) +
      ",\"entry_seconds\":" + String(home.entrySeconds) +
      ",\"hold_seconds\":" + String(home.holdSeconds) +
      ",\"warmup\":" + String(warmupRemaining()) +
      ",\"light_reason\":\"" + home.lightReason + "\"" +
      extra + liveRules + ventJson() + "}";

  server.send(200, "application/json", json);
}


void handleEvents() {
  if (!authorized()) return;

  uint64_t after =
      strtoull(server.arg("after").c_str(), nullptr, 10);

  uint64_t oldest =
      journal.next > 64 ? journal.next - 64 : 1;

  String json =
      "{\"stream_id\":\"" + streamId +
      "\",\"oldest\":" + String((unsigned long long)oldest) +
      ",\"next\":" + String((unsigned long long)journal.next) +
      ",\"events\":[";

  bool first = true;

  for (uint64_t seq = oldest; seq < journal.next; seq++) {
    if (seq <= after) continue;

    const Event& event = journal.events[(seq - 1) % 64];

    if (event.seq != seq) continue;

    if (!first) json += ",";

    first = false;

    json +=
        "{\"seq\":" + String((unsigned long long)event.seq) +
        ",\"time\":" + String((long long)event.epoch) +
        ",\"uptime\":" + String(event.uptime) +
        ",\"boot\":" + String(event.boot) +
        ",\"incident\":" + String(event.incident) +
        ",\"kind\":\"" + event.kind + "\"}";
  }

  server.send(200, "application/json", json + "]}");
}


void setup() {
  // Quat TAT ngay dau setup().
  pinMode(FAN_PIN, OUTPUT);
  digitalWrite(FAN_PIN, FAN_ACTIVE_HIGH ? LOW : HIGH);

  Serial.begin(115200);

  pinMode(PIR_PIN, INPUT_PULLDOWN);
  pinMode(LIGHT_PIN, OUTPUT);
  pinMode(ALARM_PIN, OUTPUT);

  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(OBSTACLE_PIN, INPUT_PULLUP);
  pinMode(RAIN_PIN, INPUT_PULLUP);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  digitalWrite(TRIG_PIN, LOW);
  digitalWrite(LIGHT_PIN, LOW);
  digitalWrite(ALARM_PIN, LOW);

  obstacleInput.begin(
      digitalRead(OBSTACLE_PIN) ==
          (OBSTACLE_ACTIVE_LOW ? LOW : HIGH),
      millis()
  );

  rainInput.begin(
      digitalRead(RAIN_PIN) ==
          (RAIN_ACTIVE_LOW ? LOW : HIGH),
      millis()
  );

  servo.setPeriodHertz(50);
  servo.attach(SERVO_PIN, 500, 2400);

  clothesServo.setPeriodHertz(50);
  clothesServo.attach(CLOTHES_SERVO_PIN, 500, 2400);

  buzzerReady =
      BUZZER_PASSIVE ?
      ledcAttachChannel(
          BUZZER_PIN,
          BUZZER_FREQUENCY,
          8,
          BUZZER_PWM_CHANNEL
      ) :
      true;

  if (!buzzerReady) {
    Serial.println(
        "[Buzzer] PWM init failed; check LEDC channel allocation."
    );
  }

  outputs();

  settings.begin("smarthome", false);

  clothesline.autoRain =
      settings.getBool("clothesAuto", true);

  if (settings.getBytesLength("journal3") == sizeof(journal)) {
    settings.getBytes("journal3", &journal, sizeof(journal));
  }

  if (journal.magic != 0x53483331 || journal.next == 0) {
    memset(&journal, 0, sizeof(journal));

    journal.magic = 0x53483331;
    journal.generation = esp_random();
    journal.next = 1;
  }

  journal.boot++;

  streamId =
      String((unsigned long long)ESP.getEfuseMac(), HEX) +
      "-" +
      String(journal.generation, HEX);

  home.nightLight = false;
  home.doorReminder = settings.getBool("door", true);

  home.exitSeconds = 0;

  home.entrySeconds = 0;

  home.holdSeconds =
      constrain(settings.getUInt("hold", 30), 5U, 120U);


  home.onEvent = recordEvent;

  recordEvent("restarted");

  if (rainInput.value) recordEvent("rain_started");

  const uint8_t restored = settings.getUChar("mode3", 0);

  home.setMode(
      restored == 0 ? HomeController::HOME : HomeController::AWAY,
      false
  );

  dht.begin();

  savedSsid = settings.getString("wifi_ssid", "");
  savedPass = settings.getString("wifi_pass", "");

  if (savedSsid.length()) {
    Serial.println(
        "[WiFi] Using settings saved on the setup page "
        "(override WIFI_SSID/WIFI_PASS)."
    );
  }

  if (savedSsid.length() == 0 && strlen(WIFI_SSID) > 0) {
    savedSsid = WIFI_SSID;
    savedPass = WIFI_PASS;
  }

  if (savedSsid.length()) {
    Serial.printf(
        "[WiFi] Connecting to SSID: %s\n",
        savedSsid.c_str()
    );
  }

  WiFi.onEvent(onWifiEvent);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  wifiStartedAt = millis();

  if (savedSsid.length()) {
    WiFi.begin(savedSsid.c_str(), savedPass.c_str());
  }
  else {
    startSetupPortal();
  }

  configTime(
      7 * 3600,
      0,
      "pool.ntp.org",
      "time.google.com"
  );

  const char* headers[] = {"X-Device-Key"};
  server.collectHeaders(headers, 1);

  server.on("/", HTTP_GET, handleHomePage);

  server.on("/wifi", HTTP_POST, []() {
    if (!setupPortal ||
        server.client().localIP() != WiFi.softAPIP()) {

      server.send(
          403,
          "text/plain",
          "Use the setup hotspot"
      );

      return;
    }

    String ssid = server.arg("ssid");
    String password = server.arg("password");

    if (ssid.length() == 0 ||
        ssid.length() > 32 ||
        password.length() > 63 ||
        (password.length() > 0 && password.length() < 8)) {

      server.send(
          400,
          "text/plain",
          "Check SSID/password length"
      );

      return;
    }

    settings.putString("wifi_ssid", ssid);
    settings.putString("wifi_pass", password);

    server.send(
        200,
        "text/html; charset=utf-8",
        "<meta charset='utf-8'>"
        "<p>Đã lưu Wi-Fi. ESP32 sẽ khởi động lại. "
        "Hãy đưa điện thoại về Wi-Fi vừa nhập.</p>"
    );

    rebootAt = millis() + 2000;
  });

  server.on("/sensor", HTTP_GET, handleSensor);
  server.on("/events", HTTP_GET, handleEvents);

  server.on("/clothesline", HTTP_POST, []() {
    if (!authorized()) return;

    String value = server.arg("state");

    if (value != "0" && value != "1") {
      server.send(
          400,
          "text/plain",
          "Invalid clothesline state"
      );

      return;
    }

    readExtraSensors(true);

    bool raining =
        rainInput.value ||
        digitalRead(RAIN_PIN) ==
            (RAIN_ACTIVE_LOW ? LOW : HIGH);

    if (!clothesline.setRetracted(value == "1", raining)) {
      server.send(
          412,
          "text/plain",
          "Rain protection prevents extending the clothesline"
      );

      return;
    }

    outputs();

    recordEvent(
        clothesline.retracted ?
        "clothes_retracted" :
        "clothes_extended"
    );

    server.send(200, "text/plain", "OK");
  });

  server.on("/clothesline/auto", HTTP_POST, []() {
    if (!authorized()) return;

    String value = server.arg("enabled");

    if (value != "0" && value != "1") {
      server.send(400, "text/plain", "Invalid rain rule");
      return;
    }

    clothesline.autoRain = value == "1";

    settings.putBool("clothesAuto", clothesline.autoRain);

    readExtraSensors(true);

    if (clothesline.tick(
            rainInput.value ||
            digitalRead(RAIN_PIN) ==
                (RAIN_ACTIVE_LOW ? LOW : HIGH))) {

      recordEvent("clothes_rain_retract");
    }

    outputs();

    recordEvent("clothes_rule_changed");

    server.send(200, "text/plain", "OK");
  });

  server.on("/relay", HTTP_GET, []() {
    if (!authorized()) return;

    String dev = server.arg("dev");
    String value = server.arg("state");

    if ((value != "0" && value != "1") ||
        (dev != "light" && dev != "door" && dev != "alarm")) {

      server.send(400, "text/plain", "Invalid command");
      return;
    }

    if (dev == "door" && value == "0" && blockedClose()) {
      return;
    }

    if (!home.manual(dev.c_str(), value == "1", millis())) {
      server.send(
          409,
          "text/plain",
          "Disarm protection first"
      );

      return;
    }

    outputs();

    server.send(200, "text/plain", "OK");
  });

  server.on("/mode", HTTP_POST, []() {
    if (!authorized()) return;

    String mode = server.arg("value");

    if (mode != "HOME" && mode != "AWAY") {
      server.send(400, "text/plain", "Invalid mode");
      return;
    }

    readExtraSensors(true);
    home.setMode(mode == "AWAY" ? HomeController::AWAY : HomeController::HOME,
                 unsafeDoorZone());

    settings.putUChar("mode3", home.mode);

    outputs();

    server.send(200, "text/plain", "OK");
  });

  server.on("/rules", HTTP_GET, []() {
    if (!authorized()) return;

    String night = server.arg("night");
    String door = server.arg("door");

    if (night != "0" ||
        (door != "0" && door != "1")) {

      server.send(400, "text/plain", "Invalid rules");
      return;
    }

    home.nightLight = night == "1";
    home.doorReminder = door == "1";

    saveConfig();

    server.send(200, "text/plain", "OK");
  });

  server.on("/clock", HTTP_POST, []() {
    if (!authorized()) return;

    int64_t epoch =
        strtoll(server.arg("epoch").c_str(), nullptr, 10);

    if (epoch < 1700000000 || epoch > 4102444800LL) {
      server.send(400, "text/plain", "Invalid clock");
      return;
    }

    if (!clockReady()) {
      struct timeval tv{(time_t)epoch, 0};
      settimeofday(&tv, nullptr);
    }

    server.send(200, "text/plain", "OK");
  });

  // MQ-2 / cua so / quat.
  analogSetPinAttenuation(MQ2_PIN, ADC_11db);
  windowServo.setPeriodHertz(50);
  windowServo.attach(WINDOW_SERVO_PIN, 500, 2400);
  smoke.autoEnabled = settings.getBool("smkAuto", true);
  smoke.thresholdPpm = constrain(
      settings.getInt("smkThr", SMOKE_DEFAULT_PPM),
      SMOKE_MIN_PPM, SMOKE_MAX_PPM);
  ventOutputs();

  server.on("/window", HTTP_POST, []() { handleVentToggle(true); });
  server.on("/fan", HTTP_POST, []() { handleVentToggle(false); });
  server.on("/smoke/config", HTTP_POST, handleSmokeConfig);

  server.begin();

  Serial.println(
      "DHT22 GPIO4, PIR GPIO32, FC51 GPIO27, "
      "SRF05 GPIO16/17, rain GPIO33, "
      "door servo GPIO18, clothesline servo GPIO19, "
      "buzzer GPIO14, green LED25, yellow LED26."
  );

  Serial.println(
      BUZZER_PASSIVE ?
      "Buzzer: passive, PWM 2000 Hz on GPIO14." :
      "Buzzer: active, HIGH/LOW on GPIO14."
  );

  Serial.println(
      "PIR settling for 60s. Default: IP-only LAN, no device key."
  );
}


void loop() {
  static bool reported = false;

  if (WiFi.status() == WL_CONNECTED) {
    if (!reported) {
      Serial.print("ESP32 IP: ");
      Serial.println(WiFi.localIP());

      reported = true;
    }

    if (setupPortal && rebootAt == 0) {
      WiFi.softAPdisconnect(true);
      WiFi.mode(WIFI_STA);

      setupPortal = false;
    }
  }
  else {
    reported = false;

    if (!setupPortal && millis() - wifiStartedAt >= 20000) {
      startSetupPortal();
    }
  }

  server.handleClient();

  if (rebootAt && (int32_t)(millis() - rebootAt) >= 0) {
    ESP.restart();
  }

  readExtraSensors();

  if (clothesline.tick(rainInput.value)) {
    recordEvent("clothes_rain_retract");
    outputs();
  }

  if (millis() - lastDht >= 2500) {
    lastDht = millis();

    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (!isnan(t) && !isnan(h)) {
      temperature = t;
      humidity = h;
      lastGoodDht = millis();
    }
  }

  struct tm local{};

  bool night =
      clockReady() &&
      getLocalTime(&local, 0) &&
      (local.tm_hour >= 18 || local.tm_hour < 6);

  if (!pirReady && millis() >= 60000) {
    pirReady = true;
  }

  home.tick(millis(), pirReady && digitalRead(PIR_PIN) == HIGH, unsafeDoorZone());

  // Dong cua sau 5 giay khong phat hien vat can.
  autoCloseDoor();

  smokeVentTick();

  outputs();
  ventOutputs();

  delay(5);
}
