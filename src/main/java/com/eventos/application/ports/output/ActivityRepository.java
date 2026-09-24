package com.eventos.application.ports.output;

import com.eventos.domain.model.Activity;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository {
    Activity save(Activity activity);
    Optional<Activity> findById(Long id);
    List<Activity> findByEventId(Long eventId);
    List<Activity> findByIds(List<Long> ids);
    void updateEnrollments(Long activityId, int currentEnrollments);
}
