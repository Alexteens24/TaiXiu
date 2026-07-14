package com.cortezromeo.taixiu.api.event;

import com.cortezromeo.taixiu.api.SessionSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SessionResultEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final SessionSnapshot sessionData;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public SessionResultEvent(SessionSnapshot sessionData) {
        this.sessionData = sessionData;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public SessionSnapshot getSessionData() {
        return this.sessionData;
    }

}
