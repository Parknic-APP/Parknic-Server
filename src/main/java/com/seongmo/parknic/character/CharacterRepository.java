package com.seongmo.parknic.character;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CharacterRepository {

    private final JdbcClient jdbcClient;

    public Optional<String> findNameById(Short id) {
        return jdbcClient.sql("SELECT name FROM characters WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }
}
