package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;

/** Preenchimento de caixas de seleção preservando a escolha atual (por identificador). */
final class Combos {
    private Combos() {
    }

    static void fillEvents(JComboBox<EventView> combo, List<EventView> events) {
        Long selected = combo.getSelectedItem() instanceof EventView e ? e.id() : null;
        combo.removeAllItems();
        EventView toSelect = null;
        for (EventView event : events) {
            combo.addItem(event);
            if (Objects.equals(event.id(), selected)) toSelect = event;
        }
        if (toSelect != null) combo.setSelectedItem(toSelect);
    }

    static void fillActivities(JComboBox<ActivityView> combo, List<ActivityView> activities) {
        Long selected = combo.getSelectedItem() instanceof ActivityView a ? a.id() : null;
        combo.removeAllItems();
        ActivityView toSelect = null;
        for (ActivityView activity : activities) {
            combo.addItem(activity);
            if (Objects.equals(activity.id(), selected)) toSelect = activity;
        }
        if (toSelect != null) combo.setSelectedItem(toSelect);
    }
}
