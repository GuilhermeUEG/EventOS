package com.eventos.application.ports.output;

import com.eventos.domain.Participant;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository {
    Participant save(Participant participant);
    Optional<Participant> findById(Long id);
    Optional<Participant> findByEmail(String email);
    List<Participant> findAll();
    void deleteById(Long id);
}
