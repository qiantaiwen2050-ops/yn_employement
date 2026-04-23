package com.yn.employment.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker of active JWT sessions, keyed by user id → last-seen epoch ms.
 * Stale entries (older than expireMs) are pruned on read; this gives an approximate
 * "online users now" count for the system monitor without the cost of a real session store.
 */
@Component
public class JwtSessionTracker {

    private static final long EXPIRE_MS = 5 * 60_000L;  // 5 minutes idle = considered offline

    private final Map<Long, Long> lastSeen = new ConcurrentHashMap<>();

    public void touch(Long userId) {
        if (userId != null) lastSeen.put(userId, System.currentTimeMillis());
    }

    public void remove(Long userId) {
        if (userId != null) lastSeen.remove(userId);
    }

    public int activeCount() {
        long cutoff = System.currentTimeMillis() - EXPIRE_MS;
        lastSeen.entrySet().removeIf(e -> e.getValue() < cutoff);
        return lastSeen.size();
    }
}
