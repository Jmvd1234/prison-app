package com.example.myapplication;

import com.example.myapplication.db.ProfileDao;
import java.util.UUID;

/** One earned callback can grant at most one slot, including duplicate SDK callbacks. */
final class SlotRewardSession {
    private final ProfileDao dao;
    private final String token;
    private volatile boolean earned;
    SlotRewardSession(ProfileDao dao) { this(dao, UUID.randomUUID().toString()); }
    SlotRewardSession(ProfileDao dao, String token) { this.dao = dao; this.token = token; }
    void onEarnedReward() {
        // Commit immediately; closing or rotating the ad must not discard an earned slot.
        dao.grantProfileSlot(token);
        earned = true;
    }
    boolean earned() { return earned; }
}
