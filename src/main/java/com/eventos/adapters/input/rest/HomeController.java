package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.HomeActivityResponse;
import com.eventos.adapters.input.rest.ApiDtos.HomeEventResponse;
import com.eventos.adapters.input.rest.ApiDtos.HomeResponse;
import com.eventos.adapters.input.rest.ApiDtos.HomeSpeakerResponse;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.Speaker;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conteúdo público da página inicial (RF-11, RF-14): eventos em destaque, programação e pessoas em destaque.
 * Só expõe eventos publicamente visíveis (RN-04); a escolha do que destacar fica no caso de uso.
 */
class HomeController {
    private static final int FEATURED_EVENTS = 6;
    private static final int FEATURED_ACTIVITIES = 6;
    private static final int FEATURED_SPEAKERS = 6;

    private final EventUseCase eventUseCase;

    HomeController(EventUseCase eventUseCase) {
        this.eventUseCase = eventUseCase;
    }

    void register(Javalin app) {
        app.get("/api/home", this::home);
    }

    private void home(Context ctx) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> featured = eventUseCase.featuredEvents(FEATURED_EVENTS, now);
        List<Event> visible = eventUseCase.filterEvents(null, null, null, null, false);
        int upcoming = (int) visible.stream().filter(e -> !e.getPeriod().getEnd().isBefore(now)).count();

        List<HomeEventResponse> events = featured.stream().map(e -> toEvent(e, now)).toList();
        ctx.json(new HomeResponse(visible.size(), upcoming, events, activities(featured, now), speakers(featured)));
    }

    private static HomeEventResponse toEvent(Event e, LocalDateTime now) {
        List<String> tracks = e.getActivities().stream()
                .map(a -> a.getLocation() != null ? a.getLocation().getSpaceOrTrack() : null)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();
        return new HomeEventResponse(e.getId(), e.getTitle(), e.getDescription(), ApiMapper.period(e.getPeriod()),
                e.getStatus().getDescription(), e.isEnrollmentOpen(), e.getRegistrationDeadline(),
                e.getActivities().size(), e.getMaxCapacity(), tracks, !e.getPeriod().getEnd().isBefore(now));
    }

    /** Próximas atividades dos eventos em destaque; se tudo já passou, as primeiras da programação. */
    private static List<HomeActivityResponse> activities(List<Event> featured, LocalDateTime now) {
        List<Pair> all = new ArrayList<>();
        for (Event e : featured) {
            for (Activity a : e.getActivities()) all.add(new Pair(e, a));
        }
        Comparator<Pair> byStart = Comparator.comparing((Pair p) -> p.activity().getPeriod().getStart());
        List<Pair> coming = all.stream().filter(p -> !p.activity().getPeriod().getEnd().isBefore(now)).sorted(byStart).toList();
        List<Pair> chosen = coming.isEmpty() ? all.stream().sorted(byStart).toList() : coming;
        return chosen.stream().limit(FEATURED_ACTIVITIES).map(p -> {
            Activity a = p.activity();
            return new HomeActivityResponse(a.getId(), p.event().getId(), p.event().getTitle(), a.getTitle(),
                    a.getType().getDescription(), ApiMapper.period(a.getPeriod()),
                    a.getLocation() != null ? a.getLocation().getRoom() : "",
                    a.getLocation() != null ? a.getLocation().getSpaceOrTrack() : "",
                    a.getSpeakers().stream().map(Speaker::getName).toList());
        }).toList();
    }

    /** Pessoas em destaque (RF-14): cada pessoa aparece uma vez, na primeira atividade em que atua. */
    private static List<HomeSpeakerResponse> speakers(List<Event> featured) {
        Set<String> seen = new HashSet<>();
        List<HomeSpeakerResponse> result = new ArrayList<>();
        for (Event e : featured) {
            for (Activity a : e.getActivities()) {
                for (Speaker s : a.getSpeakers()) {
                    if (result.size() < FEATURED_SPEAKERS && seen.add(s.getName().toLowerCase(Locale.ROOT))) {
                        result.add(new HomeSpeakerResponse(s.getName(), s.getRoleInActivity(), s.getBio(), s.getPhotoUrl(),
                                e.getId(), e.getTitle(), a.getTitle()));
                    }
                }
            }
        }
        return result;
    }

    private record Pair(Event event, Activity activity) {
    }
}
