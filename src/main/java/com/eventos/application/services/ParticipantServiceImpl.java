package com.eventos.application.services;

import com.eventos.application.ports.input.ParticipantUseCase;
import com.eventos.application.ports.output.ParticipantRepository;
import com.eventos.domain.Participant;
import java.util.List;
import java.util.Optional;

public class ParticipantServiceImpl implements ParticipantUseCase {
    private final ParticipantRepository participantRepository;

    public ParticipantServiceImpl(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Participant register(Participant participant) {
        // Business rule: Check for duplicate e-mail (RN-01, RF-01)
        Optional<Participant> existing = participantRepository.findByEmail(participant.getEmail());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("E-mail duplicado: este endereço já está cadastrado.");
        }
        
        // Simple mock password hashing (RNF-05)
        String mockHash = "hash_" + participant.getPasswordHash();
        participant.setPasswordHash(mockHash);
        
        return participantRepository.save(participant);
    }

    @Override
    public Optional<Participant> login(String email, String password) {
        Optional<Participant> participantOpt = participantRepository.findByEmail(email);
        if (participantOpt.isPresent()) {
            Participant participant = participantOpt.get();
            String expectedHash = "hash_" + password;
            if (participant.getPasswordHash().equals(expectedHash)) {
                return Optional.of(participant);
            }
        }
        return Optional.empty();
    }

    @Override
    public Participant getParticipant(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Participante não encontrado com ID: " + id));
    }

    @Override
    public List<Participant> listParticipants() {
        return participantRepository.findAll();
    }
}
