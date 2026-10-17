package com.s24.performance;

public final class RootCommands {
    private static final String BIN = "/data/local/tmp/czd1-0400-root";
    private RootCommands() {}
    private static String root(String cmd) { return BIN + " -c '" + cmd + "'"; }
    public static String verify() { return root("/system/bin/sh -c \"test $(id -u) -eq 0\""); }
    public static String hdmi100() { return root("/system/bin/sh /data/local/tmp/100hz.sh"); }
    public static String codm(boolean enable) {
        return root("/system/bin/sh /data/local/tmp/codm-fps120.sh " + (enable ? "activar" : "restaurar"));
    }
    // Only numeric inputs that were checked in UI. Defaults match the confirmed device policies.
    public static String enforce(int[] cpu, int gpuMHz) {
        if (cpu.length != 4 || gpuMHz < 100 || gpuMHz > 1500)
            throw new IllegalArgumentException("Frecuencias inválidas");
        int[] policies = {0, 2, 5, 7};
        int[] hardware = {2265600, 3148800, 2956800, 3398400};
        StringBuilder cmd = new StringBuilder("/system/bin/sh -c \"");
        for (int i = 0; i < 4; i++) {
            if (cpu[i] < 100000 || cpu[i] > hardware[i])
                throw new IllegalArgumentException("Frecuencia CPU fuera del rango");
            String path = "/sys/devices/system/cpu/cpufreq/policy" + policies[i] + "/";
            cmd.append("echo ").append(cpu[i]).append(" > ").append(path).append("scaling_max_freq; ");
            cmd.append("echo ").append(cpu[i]).append(" > ").append(path).append("scaling_min_freq; ");
        }
        cmd.append("echo ").append(gpuMHz).append(" > /sys/kernel/gpu/gpu_max_clock; ");
        cmd.append("echo ").append(gpuMHz).append(" > /sys/kernel/gpu/gpu_min_clock\"");
        return root(cmd.toString());
    }
}
