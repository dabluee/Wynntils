/*
 * Copyright © Wynntils 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.models.abilities.event;

import com.wynntils.models.abilities.type.ArcherHound;
import net.minecraft.core.Position;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.bus.api.Event;

public abstract class HoundEvent extends Event {
    protected HoundEvent() {}

    /**
     * Fired when the hound's timer Wolf is bound to the visible hound
     */
    public static class Activated extends HoundEvent {
        private final Position position;

        public Activated(Position position) {
            super();
            this.position = position;
        }

        public Position getPosition() {
            return position;
        }
    }

    /**
     * Fired when the hound's timer is updated (when it decreases by 1 sec)
     */
    public static class Updated extends HoundEvent {
        private final int time;
        private final Position position;

        public Updated(int time, Position position) {
            super();
            this.time = time;
            this.position = position;
        }

        public int getTime() {
            return time;
        }

        public Position getPosition() {
            return position;
        }
    }

    /**
     * Fired when a hound is removed in-game
     */
    public static class Removed extends HoundEvent {
        private final ArcherHound hound;

        public Removed(ArcherHound hound) {
            super();
            this.hound = hound;
        }

        public ArcherHound getHound() {
            return hound;
        }
    }

    /**
     * Fired when hound is initially summoned by spell cast
     */
    public static class Summoned extends HoundEvent {
        private final Wolf houndEntity;

        public Summoned(Wolf houndEntity) {
            super();
            this.houndEntity = houndEntity;
        }

        public Wolf getHoundEntity() {
            return houndEntity;
        }
    }
}
