package com.seongmo.parknic.character.repository;

import com.seongmo.parknic.character.entity.ParknicCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CharacterRepository extends JpaRepository<ParknicCharacter, Short> {

    @Query("select c.name from ParknicCharacter c where c.id = :id")
    Optional<String> findNameById(@Param("id") Short id);
}
