package com.seongmo.parknic.character.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "characters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParknicCharacter {

    @Id
    private Short id;

    @Column(nullable = false, length = 20)
    private String name;
}
