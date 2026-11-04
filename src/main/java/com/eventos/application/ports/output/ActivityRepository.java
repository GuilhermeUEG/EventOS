package com.eventos.application.ports.output;

import com.eventos.domain.model.Activity;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository {
    Activity save(Activity activity);

    Optional<Activity> findById(Long id);

    List<Activity> findByEventId(Long eventId);

    List<Activity> findByIds(List<Long> ids);

    /**
     * Reserva uma vaga de forma atômica: só incrementa se a atividade não controla vagas ou ainda
     * tem capacidade. Devolve falso quando a atividade lotou entre a verificação e a gravação.
     */
    boolean reserveSlot(Long activityId);

    void releaseSlot(Long activityId);
}
