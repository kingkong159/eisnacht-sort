package de.eisnacht.sort;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * EisNacht Sortieren: Fügt in Inventar und Kisten einen Knopf (rechts neben dem Fenster) hinzu.
 * Klick = sortieren. Funktioniert auf jedem Server, weil nur normale Inventar-Klicks geschickt werden.
 */
public class EisNachtSort implements ClientModInitializer {

    static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath("eisnachtsort", "sort"),
            Identifier.fromNamespaceAndPath("eisnachtsort", "sort_hover"));

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            String name = screen.getClass().getSimpleName();
            if (name.contains("Creative")) return;                       // Kreativ-Inventar hat eigene Logik

            int left = intField(cs, "leftPos", (width - 176) / 2);
            int top = intField(cs, "topPos", (height - 166) / 2);
            int imgW = intField(cs, "imageWidth", 176);
            int imgH = intField(cs, "imageHeight", 166);
            int x = left + imgW + 3;

            if (Sorter.hasSortableContainer(cs)) {
                // Knopf 1: Kiste sortieren (oben), Knopf 2: eigenes Inventar (bei den Inventar-Reihen)
                ImageButton chest = new ImageButton(x, top + 4, 16, 16, SPRITES, b -> Sorter.sort(cs, true));
                chest.setTooltip(Tooltip.create(Component.literal("§bKiste sortieren")));
                addWidget(screen, chest);
                ImageButton inv = new ImageButton(x, top + imgH - 82, 16, 16, SPRITES, b -> Sorter.sort(cs, false));
                inv.setTooltip(Tooltip.create(Component.literal("§bInventar sortieren")));
                addWidget(screen, inv);
            } else {
                ImageButton inv = new ImageButton(x, top + 4, 16, 16, SPRITES, b -> Sorter.sort(cs, false));
                inv.setTooltip(Tooltip.create(Component.literal("§bInventar sortieren")));
                addWidget(screen, inv);
            }
        });
    }

    /** Fügt einen Knopf ins Menü ein: zuerst über Minecrafts eigene Methode, sonst über Fabric API. */
    @SuppressWarnings("unchecked")
    static void addWidget(Object screen, AbstractWidget w) {
        Class<?> c = screen.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
                    try { m.setAccessible(true); m.invoke(screen, w); return; } catch (Throwable ignored) { }
                }
            }
            c = c.getSuperclass();
        }
        try {
            Class<?> screens = Class.forName("net.fabricmc.fabric.api.client.screen.v1.Screens");
            for (String n : new String[]{"getWidgets", "getButtons"}) {
                try {
                    Method m = null;
                    for (Method x : screens.getMethods()) if (x.getName().equals(n) && x.getParameterCount() == 1) m = x;
                    if (m != null) { ((List<Object>) m.invoke(null, screen)).add(w); return; }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
    }

    /** Liest geschützte Felder wie leftPos über ihren Namen (26.x ist unverschleiert). */
    static int intField(Object o, String name, int fallback) {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return fallback;
            }
        }
        return fallback;
    }
}
