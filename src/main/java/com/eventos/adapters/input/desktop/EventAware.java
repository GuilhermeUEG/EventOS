package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import java.util.List;

/** Tela que precisa ser avisada quando a lista de eventos é recarregada. */
interface EventAware {
    void onEventsLoaded(List<EventView> events);
}
