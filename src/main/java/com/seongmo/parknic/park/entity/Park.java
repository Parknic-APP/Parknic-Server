package com.seongmo.parknic.park.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Park {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manage_no", nullable = false, length = 50)
    private String manageNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "park_type", nullable = false, length = 20)
    private ParkType parkType;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "area_m2", nullable = false)
    private double areaM2;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "review_only", nullable = false)
    private boolean reviewOnly;
}
