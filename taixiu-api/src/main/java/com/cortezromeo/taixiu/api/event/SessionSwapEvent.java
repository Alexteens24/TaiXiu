package com.cortezromeo.taixiu.api.event;

import com.cortezromeo.taixiu.api.SessionSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SessionSwapEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final SessionSnapshot oldSessionData;
    private final SessionSnapshot newSessionData;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public SessionSwapEvent(SessionSnapshot oldSessionData, SessionSnapshot newSessionData) {
        this.oldSessionData = oldSessionData;
        this.newSessionData = newSessionData;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public SessionSnapshot getOldSessionData() {
        return this.oldSessionData;
    }

    public SessionSnapshot getNewSessionData() {
        return this.newSessionData;
    }

}
