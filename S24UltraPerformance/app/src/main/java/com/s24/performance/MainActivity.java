package com.s24.performance;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int WHITE = 0xffe7f0fa;
    private final int[] policies = {0,2,5,7};
    private final int[] defaults = {2265600,3148800,2956800,3398400};
    private LinearLayout column;
    private TextView status;
    private SharedPreferences prefs;
    private boolean updatingFps;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        home();
    }

    private TextView text(String message, int sp) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(sp);
        view.setTextColor(WHITE);
        view.setPadding(12, 14, 12, 14);
        return view;
    }
    private Button button(String label, Runnable task) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> task.run());
        return b;
    }
    private void screen(String heading) {
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(20, 24, 20, 32);
        column.setBackgroundColor(0xff101923);
        scroll.addView(column);
        setContentView(scroll);
        column.addView(text(heading, 23));
        LinearLayout nav = new LinearLayout(this);
        Button main = button("Principal", this::home);
        Button floating = button("Widgets flotantes", this::widgets);
        nav.addView(main, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(floating, new LinearLayout.LayoutParams(0, -2, 1));
        column.addView(nav);
        status = text("Listo. ADB aún no verificado en esta pantalla.", 13);
        column.addView(status);
    }
    private void report(String result) {
        if (status != null) status.setText(result);
    }
    private void run(String title, String cmd) {
        try {
            AdbBridge.get().executeAsync(cmd, (out,ok) ->
                    runOnUiThread(() -> report(title + ": " + (ok ? "OK" : "ERROR") + " " + out)));
        } catch (Exception e) { report(e.toString()); }
    }
    private EditText field(String title, String value, boolean numeric) {
        column.addView(text(title, 14));
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextColor(WHITE);
        edit.setSingleLine(true);
        if (numeric) edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        column.addView(edit);
        return edit;
    }
    private Switch toggle(String label, String key, boolean defaultValue) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(WHITE);
        s.setChecked(prefs.getBoolean(key, defaultValue));
        s.setOnCheckedChangeListener((v,checked) -> prefs.edit().putBoolean(key,checked).apply());
        column.addView(s);
        return s;
    }
    private void adbControls() {
        column.addView(text("ADB inalámbrico integrado (sin Shizuku)", 18));
        column.addView(text("Ajustes > Opciones de desarrollador > Depuración inalámbrica. Primero usa puerto de EMPAREJAMIENTO y código de 6 dígitos. Después usa el puerto distinto de CONEXIÓN.", 13));
        EditText host = field("Host local", "127.0.0.1", false);
        EditText port = field("Puerto ADB", "", true);
        EditText code = field("Código de emparejamiento", "", true);
        column.addView(button("1. Emparejar", () -> {
            try {
                AdbBridge.get().pairAsync(host.getText().toString().trim(),
                        Integer.parseInt(port.getText().toString().trim()),
                        code.getText().toString().trim(),
                        (msg,ok) -> runOnUiThread(() -> report(msg)));
            } catch (Exception e) { report(e.toString()); }
        }));
        column.addView(button("2. Conectar (introduce el puerto de conexión)", () -> {
            try {
                AdbBridge.get().connectAsync(host.getText().toString().trim(),
                        Integer.parseInt(port.getText().toString().trim()),
                        (msg,ok) -> runOnUiThread(() -> report(msg)));
            } catch (Exception e) { report(e.toString()); }
        }));
        column.addView(button("3. Verificar root temporal", () -> run("ROOT UID 0", RootCommands.verify())));
    }
    private void home() {
        screen("S24 ULTRA PERFORMANCE");
        adbControls();
        column.addView(text("Funciones del dispositivo", 18));
        column.addView(button("Ejecutar HDMI 100 Hz", () -> run("HDMI", RootCommands.hdmi100())));
        Switch fps = new Switch(this);
        fps.setText("COD Mobile 120 FPS / desactivar restaura 90");
        fps.setTextColor(WHITE);
        fps.setChecked(prefs.getBoolean("fps", false));
        fps.setOnCheckedChangeListener((sw, checked) -> {
            if (updatingFps) return;
            fps.setEnabled(false);
            try {
                AdbBridge.get().executeAsync(RootCommands.codm(checked), (output,ok) -> runOnUiThread(() -> {
                    fps.setEnabled(true);
                    if (ok) {
                        prefs.edit().putBoolean("fps", checked).apply();
                        report("CODM: comando completado; verifica los FPS reales en el juego.");
                    } else {
                        updatingFps = true;
                        fps.setChecked(!checked);
                        updatingFps = false;
                        report("CODM: " + output);
                    }
                }));
            } catch (Exception e) {
                fps.setEnabled(true);
                updatingFps = true;
                fps.setChecked(!checked);
                updatingFps = false;
                report(e.toString());
            }
        });
        column.addView(fps);
        column.addView(text("Frecuencias objetivo CPU (kHz)", 18));
        EditText[] cpu = new EditText[4];
        for (int i=0; i<4; i++) {
            cpu[i] = field("Policy " + policies[i],
                    "" + prefs.getInt("cpu" + policies[i], defaults[i]), true);
        }
        EditText gpu = field("Frecuencia GPU (MHz)", "" + prefs.getInt("gpu", 629), true);
        column.addView(button("Guardar frecuencias", () -> {
            try {
                SharedPreferences.Editor edit = prefs.edit();
                for (int i=0; i<4; i++) {
                    int value = Integer.parseInt(cpu[i].getText().toString());
                    if (value < 100000 || value > defaults[i]) throw new IllegalArgumentException("CPU fuera de rango");
                    edit.putInt("cpu" + policies[i], value);
                }
                int g = Integer.parseInt(gpu.getText().toString());
                if (g < 100 || g > 1500) throw new IllegalArgumentException("GPU fuera de rango");
                edit.putInt("gpu", g);
                edit.apply();
                report("Frecuencias guardadas. Los valores deben ser admitidos por el kernel.");
            } catch (Exception e) { report("Valor inválido: " + e.getMessage()); }
        }));
        toggle("Reaplicar CPU y GPU cada segundo (puede aumentar el calor)", "enforce", false);
        column.addView(button("Iniciar monitor", this::startOverlay));
        column.addView(button("Detener monitor", this::stopOverlay));
        column.addView(text("ADVERTENCIA: detener el monitor NO restaura frecuencias previas. Mantén apagada la reaplicación hasta probar el modo de recuperación.", 12));
    }
    private void widgets() {
        screen("WIDGETS FLOTANTES");
        column.addView(button("Conceder permiso sobre otras aplicaciones", () ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))));
        toggle("Widget CPU", "show_cpu", true);
        toggle("Widget GPU", "show_gpu", true);
        toggle("Bloquear widgets para no interceptar el juego", "lock_widgets", false);
        slider("Umbral rojo (°C)", "threshold", 60, 40, 85);
        slider("Tamaño (%)", "size", 100, 60, 170);
        slider("Opacidad (%)", "opacity", 85, 25, 100);
        column.addView(button("Mostrar widgets", this::startOverlay));
        column.addView(button("Ocultar widgets", this::stopOverlay));
        column.addView(text("Guarda posiciones arrastrando cada ventana. Reinicia los widgets para aplicar cambios de tamaño y bloqueo.", 13));
    }
    private void slider(String title, String key, int def, int min, int max) {
        TextView t = text(title + ": " + prefs.getInt(key,def), 14);
        column.addView(t);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max-min);
        bar.setProgress(prefs.getInt(key,def)-min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                t.setText(title + ": " + (value+min));
                if (user) prefs.edit().putInt(key,value+min).apply();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        column.addView(bar);
    }
    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
        startForegroundService(new Intent(this, OverlayService.class));
        report("Monitor iniciado. Revisa los permisos y los widgets.");
    }
    private void stopOverlay() {
        stopService(new Intent(this, OverlayService.class));
        report("Monitor detenido. Las frecuencias no se restauran automáticamente.");
    }
}
