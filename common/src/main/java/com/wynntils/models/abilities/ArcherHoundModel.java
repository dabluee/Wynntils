/*
 * Copyright © Wynntils 2026.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.models.abilities;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Model;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.labels.event.TextDisplayChangedEvent;
import com.wynntils.mc.event.AddEntityEvent;
import com.wynntils.mc.event.ChangeCarriedItemEvent;
import com.wynntils.mc.event.RemoveEntitiesEvent;
import com.wynntils.models.abilities.event.HoundEvent;
import com.wynntils.models.abilities.type.ArcherHound;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.spells.event.SpellEvent;
import com.wynntils.models.spells.type.SpellType;
import com.wynntils.utils.mc.McUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;

public final class ArcherHoundModel extends Model {
    private static final Pattern ARCHER_HOUND_TIMER =
            Pattern.compile("§b(?<username>.+)'(?:s)?§7 Hound\n(?<time>\\d+)s");
    private static final double HOUND_SEARCH_RADIUS = 1;
    private static final int HOUND_DATA_DELAY_TICKS = 2;
    private static final int CAST_MAX_DELAY_MS = 1400;
    // TODO: CAST_MAX_DELAY could be a config when model configs eventually exist
    // it kind of depends on ping and server lag, may not detect a hound was spawned at all

    private ArcherHound hound = null;
    private Integer timerlessHoundVisibleId = null; // ID of a hound that needs a timer
    private final Map<Integer, Integer> orphanedTimers =
            new HashMap<>(); // IDs of timers that can't really find a hound
    // These orphaned timers will be checked at a lower rate, they are probably timers that aren't actually hounds
    private long houndCastTimestamp = 0;

    public ArcherHoundModel() {
        super(List.of());
    }

    @SubscribeEvent
    public void onHoundSpellCast(SpellEvent.Cast e) {
        if (e.getSpellType() != SpellType.ARROW_SHIELD) return;
        houndCastTimestamp = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onHoundSpawn(AddEntityEvent e) {
        if (!Models.WorldState.onWorld()) return;
        if (Models.Character.getClassType() != ClassType.ARCHER) return;

        Entity entity = getBufferedEntity(e.getId());
        if (!(entity instanceof Wolf houndAS)) return;

        if (!isClose(houndAS.position(), McUtils.mc().player.position())) return;

        Managers.TickScheduler.scheduleLater(
                () -> {
                    // didn't come from a cast within the delay, probably not casted by the player
                    // this check needs to be ran with a delay, the cast/spawn order is not guaranteed
                    if (System.currentTimeMillis() - houndCastTimestamp > CAST_MAX_DELAY_MS
                            && !Models.Inventory.hasAutoCasterItem()) {
                        return;
                    }

                    WynntilsMod.postEvent(new HoundEvent.Summoned(houndAS));

                    hound = new ArcherHound(-1, houndAS.getId(), 60, houndAS.position());
                    timerlessHoundVisibleId = houndAS.getId();
                },
                HOUND_DATA_DELAY_TICKS);
    }

    @SubscribeEvent
    public void onTimerRename(TextDisplayChangedEvent.Text event) {
        if (!Models.WorldState.onWorld()) return;
        if (Models.Character.getClassType() != ClassType.ARCHER) return;

        Display.TextDisplay textDisplay = event.getTextDisplay();

        StyledText name = event.getText();
        if (name.isEmpty()) return;
        Matcher m = name.getMatcher(ARCHER_HOUND_TIMER);
        if (!m.matches()) return;
        if (!(m.group("username").equals(McUtils.playerName()))) return;

        int parsedTime = Integer.parseInt(m.group("time"));
        int timerId = textDisplay.getId();

        if (getBoundHound(timerId) == null) {
            // this is a new timer that needs to find a hound to link with
            findAndLinkHound(timerId, parsedTime, textDisplay);
        } else {
            updateHound(timerId, parsedTime, textDisplay);
        }
    }

    private void findAndLinkHound(int timerId, int parsedTime, Display.TextDisplay textDisplay) {
        List<Wolf> possibleHounds = McUtils.mc()
                .level
                .getEntitiesOfClass(
                        Wolf.class,
                        new AABB(
                                textDisplay.position().x - HOUND_SEARCH_RADIUS,
                                textDisplay.position().y - HOUND_SEARCH_RADIUS,
                                textDisplay.position().z - HOUND_SEARCH_RADIUS,
                                textDisplay.position().x + HOUND_SEARCH_RADIUS,
                                textDisplay.position().y + HOUND_SEARCH_RADIUS,
                                textDisplay.position().z + HOUND_SEARCH_RADIUS));

        for (Wolf possibleHound : possibleHounds) {
            if (timerlessHoundVisibleId != null && possibleHound.getId() == timerlessHoundVisibleId) {
                // we found the hound that this timer belongs to, bind it
                ArcherHound newHound = hound;

                newHound.setTimerEntityId(timerId);
                newHound.setTime(parsedTime);
                newHound.setPosition(possibleHound.position());

                WynntilsMod.postEvent(new HoundEvent.Activated(possibleHound.position()));

                timerlessHoundVisibleId = null;
                if (orphanedTimers.containsKey(timerId) && orphanedTimers.get(timerId) > 1) {
                    WynntilsMod.info("Matched an orphaned hound timer " + timerId + " to a hound " + " after "
                            + orphanedTimers.get(timerId) + " attempts.");
                    orphanedTimers.remove(timerId);
                }

                return;
            }
        }
        orphanedTimers.merge(timerId, 1, Integer::sum);
        if (orphanedTimers.get(timerId) == 2) {
            WynntilsMod.warn("Matched an unbound hound timer " + timerId + " but couldn't find a hound to bind it to.");
        }
    }

    private void updateHound(int timerId, int parsedTime, Display.TextDisplay textDisplay) {
        ArcherHound boundHound = getBoundHound(timerId);
        if (boundHound == null) return;

        if (boundHound == hound) {
            hound.setTime(parsedTime);
            hound.setPosition(textDisplay.position());

            WynntilsMod.postEvent(new HoundEvent.Updated(parsedTime, textDisplay.position()));
        }
    }

    @SubscribeEvent
    public void onHoundDestroy(RemoveEntitiesEvent e) {
        if (!Models.WorldState.onWorld()) return;

        List<Integer> destroyedEntities = e.getEntityIds();

        if (hound != null
                && (destroyedEntities.contains(hound.getTimerEntityId())
                        || destroyedEntities.contains(hound.getVisibleEntityId()))) {
            removeHound();
        }
    }

    @SubscribeEvent
    public void onClassChange(CharacterUpdateEvent e) {
        removeHound();
    }

    @SubscribeEvent
    public void onHeldItemChange(ChangeCarriedItemEvent e) {
        removeHound();
    }

    private Entity getBufferedEntity(int entityId) {
        Entity entity = McUtils.mc().level.getEntity(entityId);
        if (entity != null) return entity;

        if (entityId == -1) {
            return new Wolf(EntityType.WOLF, McUtils.mc().level);
        }

        return null;
    }

    private void removeHound() {
        WynntilsMod.postEvent(new HoundEvent.Removed(hound));
        hound = null;
        timerlessHoundVisibleId = null;
    }

    /**
     * Gets the hound bound to the given timerId.
     * @param timerId The timerId that is checked against the hounds
     * @return The hound bound to the given timerId, or null if no hound is bound
     */
    private ArcherHound getBoundHound(int timerId) {
        if (hound != null && hound.getTimerEntityId() == timerId) {
            return hound;
        }

        return null;
    }

    private boolean isClose(Position pos1, Position pos2) {
        LocalPlayer player = McUtils.player();
        double dX = player.getX() - player.xOld;
        double dZ = player.getZ() - player.zOld;
        double dY = player.getY() - player.yOld;
        double speedMultiplier = Math.sqrt((dX * dX) + (dZ * dZ) + (dY * dY)) * 10;
        // wynn never casts perfectly aligned hounds
        speedMultiplier = Math.max(speedMultiplier, 1.5);

        return Math.abs(pos1.x() - pos2.x()) < speedMultiplier
                && Math.abs(pos1.y() - pos2.y()) < (speedMultiplier * 2)
                && Math.abs(pos1.z() - pos2.z()) < speedMultiplier;
    }

    public ArcherHound getHound() {
        return hound;
    }
}
