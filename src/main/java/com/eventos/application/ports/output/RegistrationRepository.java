package com.eventos.application.ports.output;

import com.eventos.domain.model.Registration;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository {
    Registration save(Registration registration);
    Optional<Registration> findByEventAndUser(Long eventId, Long userId);
    List<Registration> findByEventId(Long eventId);
    List<Registration> findByUserId(Long userId);
    int countConfirmedByEventId(Long eventId);
    int countConfirmedByActivityId(Long activityId);
}
