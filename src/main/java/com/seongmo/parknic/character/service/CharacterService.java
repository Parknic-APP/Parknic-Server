package com.seongmo.parknic.character.service;

import com.seongmo.parknic.character.dto.CharacterListResponse;
import com.seongmo.parknic.character.dto.CharacterResponse;
import com.seongmo.parknic.character.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;

    @Transactional(readOnly = true)
    public CharacterListResponse getCharacters() {
        List<CharacterResponse> characters = characterRepository.findAll(Sort.by("id")).stream()
                .map(c -> new CharacterResponse(c.getId(), c.getName()))
                .toList();
        return new CharacterListResponse(characters);
    }
}
