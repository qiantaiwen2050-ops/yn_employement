package com.yn.employment.modules.system.monitor;

import com.yn.employment.auth.JwtSessionTracker;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system/monitor")
public class MonitorController {

    @Autowired(required = false)
    private JwtSessionTracker sessionTracker;

    @GetMapping
    public Result<Map<String, Object>> snapshot() {
        requireProvince();
        Map<String, Object> data = new HashMap<>();

        // System-wide CPU & memory (requires com.sun management interface — available on HotSpot JVM)
        OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sun = null;
        if (base instanceof com.sun.management.OperatingSystemMXBean s) sun = s;

        Map<String, Object> cpu = new HashMap<>();
        cpu.put("availableCores", base.getAvailableProcessors());
        cpu.put("systemLoadAverage", base.getSystemLoadAverage());
        cpu.put("systemCpuPct",  sun != null ? Math.round(sun.getCpuLoad()     * 10000) / 100.0 : null);
        cpu.put("processCpuPct", sun != null ? Math.round(sun.getProcessCpuLoad() * 10000) / 100.0 : null);
        data.put("cpu", cpu);

        Map<String, Object> mem = new HashMap<>();
        if (sun != null) {
            long total = sun.getTotalMemorySize();
            long free  = sun.getFreeMemorySize();
            long used  = total - free;
            mem.put("total", total);
            mem.put("used", used);
            mem.put("free", free);
            mem.put("usedPct", total == 0 ? 0 : Math.round(used * 10000.0 / total) / 100.0);
        }
        data.put("memory", mem);

        // JVM heap
        Runtime rt = Runtime.getRuntime();
        long jvmMax = rt.maxMemory();
        long jvmTotal = rt.totalMemory();
        long jvmFree = rt.freeMemory();
        long jvmUsed = jvmTotal - jvmFree;
        data.put("jvm", Map.of(
                "heapUsed", jvmUsed,
                "heapTotal", jvmTotal,
                "heapMax", jvmMax,
                "heapUsedPct", jvmMax == 0 ? 0 : Math.round(jvmUsed * 10000.0 / jvmMax) / 100.0));

        // Disk
        File root = new File("/");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;
        data.put("disk", Map.of(
                "partition", "/",
                "total", totalSpace,
                "used", usedSpace,
                "free", freeSpace,
                "usedPct", totalSpace == 0 ? 0 : Math.round(usedSpace * 10000.0 / totalSpace) / 100.0));

        // App
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        data.put("app", Map.of(
                "uptimeMs", uptimeMs,
                "uptimeHuman", formatUptime(uptimeMs),
                "startedAt", runtime.getStartTime(),
                "version", "v0.1.0",
                "onlineUsers", sessionTracker != null ? sessionTracker.activeCount() : 0
        ));
        return Result.ok(data);
    }

    private static String formatUptime(long ms) {
        Duration d = Duration.ofMillis(ms);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long mins = d.toMinutesPart();
        long secs = d.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天 ");
        if (days > 0 || hours > 0) sb.append(hours).append("小时 ");
        sb.append(mins).append("分 ").append(secs).append("秒");
        return sb.toString();
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
