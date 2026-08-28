package com.eventos.application.ports.input;

import com.eventos.domain.Participant;
import java.util.List;
import java.util.Optional;

public interface ParticipantUseCase {
    Participant register(Participant participant);
    Optional<Participant> login(String email, String password);
    Participant getParticipant(Long id);
    List<Participant> listParticipants();
}
