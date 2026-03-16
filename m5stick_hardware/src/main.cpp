#include <M5StickCPlus2.h>
#include <HijelHID_BLEKeyboard.h>

// Initialize BLE Keyboard with a unique name for Android pairing
HijelHID_BLEKeyboard bleKeyboard("StagePilot-Tap-Snyder", "StagePilot", 100);

// IMU thresholds and timing for acoustic guitar body
const float TAP_THRESHOLD = 1.5;      // G-force delta required to register a tap
const int TAP_WINDOW_MS = 350;        // Max time to wait for a second tap
const int VIBRATION_LOCKOUT_MS = 150; // Lockout to ignore guitar string ringing after a tap

// State machine variables
unsigned long lastTapTime = 0;
unsigned long lockoutEndTime = 0;
int tapCount = 0;
float baselineZ = 0.0;
bool isCalibrated = false;

// Battery update tracking
unsigned long lastBatteryUpdate = 0;

// Function Prototypes required for C++
void drawStatus(const char* status);
void calibrateIMU();
void updateScreenWithBattery(int batLevel);
void triggerVisualFeedback(uint16_t color, const char* action);


void setup() {
  // Initialize the M5StickC PLUS2
  M5.begin();

  // Set screen rotation so it's readable if mounted sideways inside the guitar
  M5.Lcd.setRotation(1);
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextColor(WHITE);
  M5.Lcd.setTextSize(2);

  // Initialize the internal IMU (MPU6886)
  M5.Imu.init();

  // Start the BLE Keyboard
  bleKeyboard.begin();

  drawStatus("Starting BLE...");

  // Calibrate baseline gravity on the Z-axis
  calibrateIMU();
}

void loop() {
  M5.update(); // Update button states (optional)

  unsigned long currentTime = millis();

  // 1. Update Battery Status to Android OS every 30 seconds
  if (currentTime - lastBatteryUpdate > 30000) {
    int batLevel = M5.Power.getBatteryLevel();
    if (bleKeyboard.isConnected()) {
      bleKeyboard.setBatteryLevel(batLevel);
    }
    updateScreenWithBattery(batLevel);
    lastBatteryUpdate = currentTime;
  }

  // 2. Check BLE Connection
  if (!bleKeyboard.isConnected()) {
    drawStatus("Waiting for Phone...");
    delay(100);
    return; // Don't process IMU if not connected
  }

  // 3. Read IMU Data
  float accX, accY, accZ;
  M5.Imu.getAccelData(&accX, &accY, &accZ);

  // High-Pass Filter: We only care about sudden, sharp changes in Z (perpendicular to soundboard)
  float deltaZ = abs(accZ - baselineZ);

  // 4. Tap Detection Logic
  if (currentTime > lockoutEndTime) {
    if (deltaZ > TAP_THRESHOLD) {
      // We detected a hard tap!
      tapCount++;
      lastTapTime = currentTime;

      // Immediately lock out the sensor so we don't count the guitar body's "ringing" vibrations as multiple taps
      lockoutEndTime = currentTime + VIBRATION_LOCKOUT_MS;

      // Optional: Quick debug flash on the screen
      M5.Lcd.fillScreen(ORANGE);
    }
  }

  // 5. State Machine Processing (Has the tap window expired?)
  if (tapCount > 0 && (currentTime - lastTapTime > TAP_WINDOW_MS)) {
    if (tapCount == 1) {
      // SINGLE TAP -> Forward (Next Page / Scroll Down)
      bleKeyboard.tap(KEY_PAGE_DOWN);
      triggerVisualFeedback(GREEN, "FORWARD");
    }
    else if (tapCount >= 2) {
      // DOUBLE TAP -> Backward (Previous Page / Scroll Up)
      bleKeyboard.tap(KEY_PAGE_UP);
      triggerVisualFeedback(BLUE, "BACKWARD");
    }

    // Reset state
    tapCount = 0;
  }

  // Run the loop roughly at 100Hz (10ms delay) to catch fast taps without flooding the CPU
  delay(10);
}

// --- Helper Functions ---

void calibrateIMU() {
  float accX, accY, accZ;
  float sumZ = 0;
  int samples = 50;

  drawStatus("Calibrating...");

  for (int i = 0; i < samples; i++) {
    M5.Imu.getAccelData(&accX, &accY, &accZ);
    sumZ += accZ;
    delay(10);
  }

  baselineZ = sumZ / samples;
  isCalibrated = true;
  drawStatus("Ready to Rock!");
}

void triggerVisualFeedback(uint16_t color, const char* action) {
  // Flash the screen the requested color so it's visible through the acoustic soundhole
  M5.Lcd.fillScreen(color);
  M5.Lcd.setCursor(10, 30);
  M5.Lcd.setTextSize(3);
  M5.Lcd.setTextColor(WHITE);
  M5.Lcd.print(action);

  // Hold the color briefly, then return to black
  delay(300);

  int batLevel = M5.Power.getBatteryLevel();
  updateScreenWithBattery(batLevel);
}

void updateScreenWithBattery(int batLevel) {
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setCursor(10, 20);
  M5.Lcd.setTextSize(2);
  M5.Lcd.setTextColor(WHITE);
  if (bleKeyboard.isConnected()) {
    M5.Lcd.println("Connected");
  } else {
    M5.Lcd.println("Disconnected");
  }
  M5.Lcd.setCursor(10, 50);
  M5.Lcd.printf("Bat: %d%%", batLevel);
}

void drawStatus(const char* status) {
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setCursor(10, 30);
  M5.Lcd.setTextSize(2);
  M5.Lcd.print(status);
}
