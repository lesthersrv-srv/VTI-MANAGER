package com.s24.performance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OverlayService extends Service {
    private WindowManager wm;
    private SharedPreferences prefs;
    private TextView cpu, gpu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean sampling = new AtomicBoolean(false);
    private volatile String cpuTemperature = "N/D", gpuTemperature = "N/D";
    private volatile String cpuUtil = "N/D", gpuUtil = "N/D", gpuMHz = "N/D";
    private long previousTotal = -1, previousIdle = -1;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        makeNotification();
        if (prefs.getBoolean("show_cpu", true)) cpu = makeWidget(true);
        if (prefs.getBoolean("show_gpu", true)) gpu = makeWidget(false);
        handler.post(runner);
    }
    private void makeNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("s24mon","S24 Monitor",NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this,OverlayService.class);
        stop.setAction("STOP");
        PendingIntent pending = PendingIntent.getService(this,13,stop,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this,"s24mon")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("S24 Performance activo")
                .setContentText("Monitor CPU y GPU")
                .addAction(new Notification.Action.Builder(null,"Detener",pending).build())
                .build();
        startForeground(13,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    private TextView makeWidget(boolean isCpu) {
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setPadding(14,10,14,10);
        label.setText(isCpu ? "CPU: leyendo..." : "GPU: leyendo...");
        int scale = prefs.getInt("size",100);
        int width = (int) (getResources().getDisplayMetrics().density * 155 * scale / 100f);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (prefs.getBoolean("lock_widgets",false))
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width,-2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = prefs.getInt(isCpu ? "cpuX" : "gpuX",20);
        p.y = prefs.getInt(isCpu ? "cpuY" : "gpuY",isCpu ? 120 : 230);
        label.setAlpha(prefs.getInt("opacity",85)/100f);
        label.setBackgroundColor(isCpu ? 0xe5224762 : 0xe5215046);
        wm.addView(label,p);
        if (!prefs.getBoolean("lock_widgets",false)) {
            label.setOnTouchListener(new View.OnTouchListener() {
                int firstX,firstY;
                float touchX,touchY;
                @Override public boolean onTouch(View v, MotionEvent ev) {
                    switch (ev.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            firstX=p.x;firstY=p.y;
                            touchX=ev.getRawX();touchY=ev.getRawY();
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_MOVE:
                            p.x=firstX+(int)(ev.getRawX()-touchX);
                            p.y=firstY+(int)(ev.getRawY()-touchY);
                            wm.updateViewLayout(label,p);
                            if (ev.getAction()==MotionEvent.ACTION_UP) prefs.edit()
                                    .putInt(isCpu ? "cpuX" : "gpuX",p.x)
                                    .putInt(isCpu ? "cpuY" : "gpuY",p.y).apply();
                            return true;
                        default:return false;
                    }
                }
            });
        }
        return label;
    }

    private final Runnable runner = new Runnable() {
        @Override public void run() {
            if (sampling.compareAndSet(false,true)) sample();
            render();
            handler.postDelayed(this,1000);
        }
    };
    private int[] freqs() {
        return new int[]{prefs.getInt("cpu0",2265600),prefs.getInt("cpu2",3148800),
                prefs.getInt("cpu5",2956800),prefs.getInt("cpu7",3398400)};
    }
    private static String read(String path) {
        try(BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        } catch(Exception e) { return null; }
    }
    private static String degrees(String value) {
        try {
            int milli = Integer.parseInt(value.trim());
            if (milli <= 0 || milli >= 125000) return "N/D";
            return String.format(Locale.US,"%.1f",milli/1000.0);
        } catch(Exception e) { return "N/D"; }
    }
    private void direct() {
        int cpuMax=0, gpuMax=0;
        for (int i=0;i<100;i++) {
            String base="/sys/class/thermal/thermal_zone"+i+"/";
            String type=read(base+"type"), value=read(base+"temp");
            if (type==null||value==null) continue;
            try {
                int temp=Integer.parseInt(value.trim());
                if(temp<0||temp>125000)continue;
                if((type.startsWith("cpu-")||type.startsWith("cpuss-"))&&temp>cpuMax)cpuMax=temp;
                if(type.startsWith("gpuss-")&&temp>gpuMax)gpuMax=temp;
            }catch(Exception ignored){}
        }
        if (cpuMax>0) cpuTemperature=degrees(""+cpuMax);
        String t=read("/sys/kernel/gpu/gpu_tmu");
        if (t!=null && !"N/D".equals(degrees(t))) gpuTemperature=degrees(t);
        else if (gpuMax>0) gpuTemperature=degrees(""+gpuMax);
        String busy=read("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        if(busy!=null)gpuUtil=busy.trim();
        String clock=read("/sys/class/kgsl/kgsl-3d0/gpuclk");
        if(clock==null)clock=read("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        if(clock!=null)try{gpuMHz=""+(Long.parseLong(clock.trim())/1000000L);}catch(Exception ignored){}
        String stat=read("/proc/stat");
        if(stat!=null) parseStat(stat);
    }
    private synchronized void parseStat(String stat) {
        try {
            String[] words=stat.trim().split("\\s+");
            if(!"cpu".equals(words[0])||words.length<9)return;
            long total=0;
            for(int i=1;i<=8;i++)total+=Long.parseLong(words[i]);
            long idle=Long.parseLong(words[4])+Long.parseLong(words[5]);
            if(previousTotal>=0 && total>previousTotal) {
                double pct=100.0*(1-(double)(idle-previousIdle)/(total-previousTotal));
                cpuUtil=String.format(Locale.US,"%.1f",Math.max(0,Math.min(100,pct)));
            }
            previousTotal=total;previousIdle=idle;
        }catch(Exception ignored){}
    }
    private final String telemetry =
            "for z in /sys/class/thermal/thermal_zone*; do " +
            "t=$(cat \"$z/type\" 2>/dev/null); v=$(cat \"$z/temp\" 2>/dev/null); " +
            "case \"$t\" in cpu-*|cpuss-*) echo \"CPU:$v\";; gpuss-*) echo \"GPUSS:$v\";; esac; " +
            "done; " +
            "echo \"GPU:$(cat /sys/kernel/gpu/gpu_tmu 2>/dev/null)\"; " +
            "echo \"BUSY:$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null)\"; " +
            "echo \"CLOCK:$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null)\"; " +
            "head -n1 /proc/stat | sed 's/^/STAT:/'";
    private void sample() {
        new Thread(() -> {
            try {
                direct();
                AdbBridge adb=AdbBridge.get();
                if (!adb.isConnected()) { sampling.set(false); return; }
                String command=telemetry;
                if (prefs.getBoolean("enforce",false)) command=RootCommands.enforce(freqs(),prefs.getInt("gpu",629))+"; "+command;
                adb.executeAsync(command,(output,ok) -> {
                    try { if (ok) parseTelemetry(output); } finally { sampling.set(false); }
                });
            }catch(Exception e){sampling.set(false);}
        },"s24-metrics").start();
    }
    private void parseTelemetry(String raw) {
        int maxCpu=0,maxGpu=0;
        for(String line:raw.split("\\r?\\n")) {
            try {
                if(line.startsWith("CPU:"))maxCpu=Math.max(maxCpu,Integer.parseInt(line.substring(4).trim()));
                else if(line.startsWith("GPUSS:"))maxGpu=Math.max(maxGpu,Integer.parseInt(line.substring(6).trim()));
                else if(line.startsWith("GPU:")&&!"N/D".equals(degrees(line.substring(4))))gpuTemperature=degrees(line.substring(4));
                else if(line.startsWith("BUSY:")&&!line.substring(5).trim().isEmpty())gpuUtil=line.substring(5).trim();
                else if(line.startsWith("CLOCK:"))gpuMHz=""+Long.parseLong(line.substring(6).trim())/1000000L;
                else if(line.startsWith("STAT:"))parseStat(line.substring(5));
            }catch(Exception ignored){}
        }
        if(maxCpu>0)cpuTemperature=degrees(""+maxCpu);
        if("N/D".equals(gpuTemperature)&&maxGpu>0)gpuTemperature=degrees(""+maxGpu);
    }
    private static boolean overThreshold(String temp, int threshold) {
        try { return Float.parseFloat(temp)>=threshold; }
        catch(Exception e){return false;}
    }
    private void render() {
        int threshold=prefs.getInt("threshold",60);
        if(cpu!=null){
            cpu.setBackgroundColor(overThreshold(cpuTemperature,threshold)?0xeec71b28:0xe5224762);
            cpu.setText("CPU  "+cpuTemperature+"°C\nUSO "+cpuUtil+"%");
        }
        if(gpu!=null){
            gpu.setBackgroundColor(overThreshold(gpuTemperature,threshold)?0xeec71b28:0xe5215046);
            gpu.setText("GPU  "+gpuTemperature+"°C\nUSO "+gpuUtil+"%   "+gpuMHz+" MHz");
        }
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(runner);
        if(cpu!=null)wm.removeView(cpu);
        if(gpu!=null)wm.removeView(gpu);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
