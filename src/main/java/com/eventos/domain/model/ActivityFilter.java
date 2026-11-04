package com.eventos.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Critérios combináveis de consulta à programação (RF-09): data, faixa de horário de início,
 * trilha/espaço, tipo, local e texto livre. Critérios nulos ou vazios não restringem o resultado.
 */
public final class ActivityFilter {
    private final LocalDate date;
    private final LocalTime startsFrom;
    private final LocalTime startsUntil;
    private final String track;
    private final ActivityType type;
    private final String room;
    private final String text;

    public ActivityFilter(LocalDate date, LocalTime startsFrom, LocalTime startsUntil,
                          String track, ActivityType type, String room, String text) {
        this.date = date;
        this.startsFrom = startsFrom;
        this.startsUntil = startsUntil;
        this.track = normalize(track);
        this.type = type;
        this.room = normalize(room);
        this.text = normalize(text);
    }

    public static ActivityFilter none() {
        return new ActivityFilter(null, null, null, null, null, null, null);
    }

    public boolean matches(Activity activity) {
        if (date != null && !activity.occursOn(date)) return false;
        LocalTime start = activity.getPeriod().getStart().toLocalTime();
        if (startsFrom != null && start.isBefore(startsFrom)) return false;
        if (startsUntil != null && start.isAfter(startsUntil)) return false;
        if (track != null && !activity.getLocation().getSpaceOrTrack().toLowerCase(Locale.ROOT).equals(track)) return false;
        if (type != null && activity.getType() != type) return false;
        if (room != null && !activity.getLocation().getRoom().toLowerCase(Locale.ROOT).equals(room)) return false;
        return text == null || searchableText(activity).contains(text);
    }

    /** Aplica o filtro e devolve as atividades em ordem cronológica. */
    public List<Activity> apply(List<Activity> activities) {
        return activities.stream()
                .filter(this::matches)
                .sorted(Comparator.comparing((Activity a) -> a.getPeriod().getStart()))
                .toList();
    }

    private String searchableText(Activity activity) {
        StringBuilder sb = new StringBuilder();
        sb.append(activity.getTitle()).append(' ').append(activity.getDescription()).append(' ')
                .append(activity.getLocation().getRoom()).append(' ').append(activity.getLocation().getSpaceOrTrack());
        activity.getSpeakers().forEach(s -> sb.append(' ').append(s.getName()));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
