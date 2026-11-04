package com.eventos.domain.policies;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Objeto de valor com o que um participante efetivamente cumpriu em um evento. É a entrada das
 * políticas de elegibilidade de certificado, que assim não dependem de repositórios.
 */
public final class ParticipationSummary {
    private final Set<Long> attendedActivityIds;
    private final int totalActivities;
    private final double hoursAttended;
    private final double totalHours;

    public ParticipationSummary(Set<Long> attendedActivityIds, int totalActivities,
                                double hoursAttended, double totalHours) {
        this.attendedActivityIds = attendedActivityIds == null
                ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(attendedActivityIds));
        this.totalActivities = Math.max(0, totalActivities);
        this.hoursAttended = Math.max(0.0, hoursAttended);
        this.totalHours = Math.max(0.0, totalHours);
    }

    public Set<Long> getAttendedActivityIds() {
        return attendedActivityIds;
    }

    public int getAttendedCount() {
        return attendedActivityIds.size();
    }

    public int getTotalActivities() {
        return totalActivities;
    }

    public double getHoursAttended() {
        return hoursAttended;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public double getAttendedPercentage() {
        return totalHours <= 0 ? 0.0 : Math.min(100.0, hoursAttended / totalHours * 100.0);
    }
}
