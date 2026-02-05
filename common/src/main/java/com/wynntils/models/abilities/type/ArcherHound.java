/*
 * Copyright © Wynntils 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.models.abilities.type;

import net.minecraft.core.Position;

public class ArcherHound {
    private final int visibleEntityId;
    private int timerEntityId;
    private int time;
    /**
     * Our internal representation of the hound/timer's position. Not guaranteed to match the position of either the
     * hound or the timer.
     * */
    private Position position;

    public ArcherHound(int timerEntityId, int visibleEntityId, int time, Position position) {
        this.timerEntityId = timerEntityId;
        this.visibleEntityId = visibleEntityId;
        this.time = time;
        this.position = position;
    }

    public int getVisibleEntityId() {
        return visibleEntityId;
    }

    public int getTimerEntityId() {
        return timerEntityId;
    }

    public void setTimerEntityId(int timerEntityId) {
        this.timerEntityId = timerEntityId;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }
}
