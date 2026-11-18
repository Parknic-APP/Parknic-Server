package com.seongmo.parknic.character.controller;

import com.seongmo.parknic.character.dto.CharacterListResponse;
import com.seongmo.parknic.character.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public CharacterListResponse getCharacters() {
        return characterService.getCharacters();
    }
}
