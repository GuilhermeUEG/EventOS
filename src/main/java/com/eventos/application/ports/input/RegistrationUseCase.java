package com.eventos.application.ports.input;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.Registration;
import java.util.List;
import java.util.Set;

public interface RegistrationUseCase {
    Registration registerForEvent(Long eventId, Long userId, Set<Long> activityIds);
    Registration addActivityToRegistration(Long eventId, Long userId, Long activityId);
    Registration removeActivityFromRegistration(Long eventId, Long userId, Long activityId);
    void cancelRegistration(Long eventId, Long userId);
    List<Registration> getEventRegistrations(Long eventId);
    List<Registration> getUserRegistrations(Long userId);
    List<Activity> getParticipantAgenda(Long userId, Long eventId);
}
