# Variables
GRADLE = ./gradlew
OUT_DIR = app/build/outputs/apk
RELEASE_APK = $(OUT_DIR)/release/app-release.apk
DEBUG_APK = $(OUT_DIR)/debug/app-debug.apk

# Phony targets to prevent conflicts with file names
.PHONY: all release debug clean install log help

# Default target (runs when you type 'make')
all: release

# --- Build Commands ---

# Build optimized release APK
release:
	$(GRADLE) assembleRelease
	@echo "\n✅ Release APK created at:"
	@echo "   $(RELEASE_APK)"

# Build debug APK (faster, no keystore needed)
debug:
	$(GRADLE) assembleDebug
	@echo "\n✅ Debug APK created at:"
	@echo "   $(DEBUG_APK)"

# --- Utility Commands ---

# Clean build artifacts
clean:
	$(GRADLE) clean

# Install Release APK via ADB (if phone is connected via USB)
install: release
	adb install -r $(RELEASE_APK)

# View logs for your app specifically
log:
	adb logcat -v time -s "MainActivity" "GnssStatus" "*:S"

# Show available commands
help:
	@echo "Available targets:"
	@echo "  make release   - Build Release APK (Default)"
	@echo "  make debug     - Build Debug APK"
	@echo "  make install   - Build Release & Install to connected device"
	@echo "  make clean     - Clean project"
	@echo "  make log       - Filter logcat for this app"
