# S24 Ultra Performance Manager (Android prototype 0.1)

Two-screen Android application: ADB wireless pairing/connection and optional root commands via /data/local/tmp/czd1-0400-root, plus floating CPU/GPU overlays.

Features:
- ADB wireless pairing without Shizuku, using libadb-android.
- Custom CPU policy 0/2/5/7 frequencies and GPU frequency. Optional 1-second reapplication, disabled by default.
- Execute already-installed /data/local/tmp/100hz.sh and /data/local/tmp/codm-fps120.sh activar/restaurar.
- Separate draggable overlays, adjustable size/opacity and a red-temperature threshold (default 60 C).
- Overlay foreground service; requires Android overlay permission and user-enabled wireless debugging.

Build: Android Studio open S24UltraPerformance, or Actions > Build S24 Ultra Performance APK > download artifact S24UltraPerformance-debug-apk. Android SDK 35 + JDK 17. The debug APK is for testing only; it has not been tested on the S24 Ultra.

ADB: Pair using host (127.0.0.1 for local device), PAIRING port and six-digit code. Connect using the distinct debugging port shown in Wireless Debugging. The system shell privilege is not root; root commands rely on the user's existing executable and files. Android Wireless Debugging can be disabled by Android or require reconnection.

Safety: Reapplying min=max CPU/GPU can increase temperatures and battery use. This does not bypass kernel thermal protections. Frequency settings are NOT automatically restored when stopping the overlay; keep reapplication off until rollback is implemented and tested. HDMI currently has no undo command. Root binary, CODM and HDMI scripts are not bundled. The application cannot guarantee that game FPS or external display mode changed until verified on hardware. This is a prototype, not a production audited app.

Privacy: ADB connection uses local ports. Its authentication private key is kept in Android Keystore. The libadb library has not been security audited.
