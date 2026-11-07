package de.eisnacht.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Sortiert mit ganz normalen Linksklicks (wie ein Spieler), deshalb erlaubt auf jedem Server.
 * 1. Gleiche Stapel zusammenlegen  2. Reihenfolge: Werkzeuge/Rüstung, Blöcke, Items – alphabetisch, volle Stapel zuerst.
 * Die Hotbar wird nicht angefasst.
 */
final class Sorter {

    private static final Set<String> CONTAINERS = Set.of("ChestMenu", "ShulkerBoxMenu", "DispenserMenu", "HopperMenu");
    static final Comparator<ItemStack> ORDER = Comparator
            .comparingInt((ItemStack s) -> s.getMaxStackSize() == 1 ? 0 : s.getItem() instanceof BlockItem ? 1 : 2)
            .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
            .thenComparing(s -> -s.getCount());
    private static Method click;
    private static Object pickup;

    static boolean hasSortableContainer(AbstractContainerScreen<?> screen) {
        return CONTAINERS.contains(screen.getMenu().getClass().getSimpleName());
    }

    static void sort(AbstractContainerScreen<?> screen, boolean container) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;                       // nichts in der Hand haben

        List<Slot> slots = new ArrayList<>();
        for (Slot s : menu.slots) {
            boolean player = s.container instanceof Inventory;
            if (container) { if (!player) slots.add(s); }
            else if (player && s.getContainerSlot() >= 9 && s.getContainerSlot() < 36) slots.add(s);
        }
        if (slots.isEmpty()) return;

        // 1) Zusammenlegen
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = slots.get(i).getItem();
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                ItemStack b = slots.get(j).getItem();
                if (b.isEmpty() || !ItemStack.isSameItemSameComponents(a, b)) continue;
                click(mc, menu, slots.get(j));
                click(mc, menu, slots.get(i));
                if (!menu.getCarried().isEmpty()) click(mc, menu, slots.get(j));
                a = slots.get(i).getItem();
                if (a.getCount() >= a.getMaxStackSize()) break;
            }
        }

        // 2) Gewünschte Reihenfolge berechnen
        List<ItemStack> want = new ArrayList<>();
        for (Slot s : slots) if (!s.getItem().isEmpty()) want.add(s.getItem().copy());
        want.sort(ORDER);

        // 3) Mit Tauschen an die richtige Stelle bringen
        for (int i = 0; i < want.size(); i++) {
            ItemStack target = want.get(i);
            if (same(slots.get(i).getItem(), target)) continue;
            int j = -1;
            for (int k = i + 1; k < slots.size(); k++) if (same(slots.get(k).getItem(), target)) { j = k; break; }
            if (j < 0) continue;
            click(mc, menu, slots.get(j));
            click(mc, menu, slots.get(i));
            if (!menu.getCarried().isEmpty()) click(mc, menu, slots.get(j));
        }
        // Sicherheit: falls doch etwas in der Hand blieb, in einen leeren Platz legen
        if (!menu.getCarried().isEmpty())
            for (Slot s : slots) if (s.getItem().isEmpty()) { click(mc, menu, s); break; }
    }

    /** Kreativmodus: Items direkt neu verteilen (Kreativ darf Plätze direkt setzen). */
    static void sortCreative() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        Inventory inv = mc.player.getInventory();
        List<ItemStack> merged = new ArrayList<>();
        for (int k = 9; k < 36; k++) {
            ItemStack s = inv.getItem(k);
            if (s.isEmpty()) continue;
            int left = s.getCount();
            for (ItemStack m : merged) {
                if (left <= 0) break;
                if (ItemStack.isSameItemSameComponents(m, s) && m.getCount() < m.getMaxStackSize()) {
                    int add = Math.min(left, m.getMaxStackSize() - m.getCount());
                    m.grow(add);
                    left -= add;
                }
            }
            if (left > 0) { ItemStack c = s.copy(); c.setCount(left); merged.add(c); }
        }
        merged.sort(ORDER);
        for (int k = 9; k < 36; k++) {
            int i = k - 9;
            ItemStack s = i < merged.size() ? merged.get(i) : ItemStack.EMPTY;
            inv.setItem(k, s.copy());
            mc.gameMode.handleCreativeModeItemAdd(s.copy(), k);     // Platz k im Spieler-Inventar-Menü
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    /** Linksklick auf einen Platz – per Namen gesucht, damit es auch bei kleinen Änderungen in 26.x klappt. */
    private static void click(Minecraft mc, AbstractContainerMenu menu, Slot slot) {
        try {
            if (click == null) {
                for (Method m : mc.gameMode.getClass().getMethods()) {
                    if (m.getName().equals("handleInventoryMouseClick") && m.getParameterCount() == 5) {
                        Class<?> type = m.getParameterTypes()[3];
                        if (type.isEnum()) for (Object c : type.getEnumConstants()) if (c.toString().equals("PICKUP")) pickup = c;
                        click = m;
                        break;
                    }
                }
            }
            if (click == null || pickup == null) return;
            click.invoke(mc.gameMode, menu.containerId, slot.index, 0, pickup, mc.player);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
