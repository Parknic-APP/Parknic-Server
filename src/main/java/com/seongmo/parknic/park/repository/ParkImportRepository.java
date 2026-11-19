package com.seongmo.parknic.park.repository;

import com.seongmo.parknic.park.dto.ParkImportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParkImportRepository {

    private final JdbcClient jdbcClient;

    public void upsert(ParkImportRow row) {
        jdbcClient.sql("""
                        INSERT INTO parks (manage_no, name, park_type, address, latitude, longitude, area_m2, radius_m)
                        VALUES (:manageNo, :name, :parkType, :address, :latitude, :longitude, :areaM2, :radiusM)
                        ON CONFLICT (manage_no) DO UPDATE SET
                            name = EXCLUDED.name,
                            park_type = EXCLUDED.park_type,
                            address = EXCLUDED.address,
                            latitude = EXCLUDED.latitude,
                            longitude = EXCLUDED.longitude,
                            area_m2 = EXCLUDED.area_m2,
                            radius_m = EXCLUDED.radius_m
                        """)
                .param("manageNo", row.manageNo())
                .param("name", row.name())
                .param("parkType", row.parkType().name())
                .param("address", row.address())
                .param("latitude", row.latitude())
                .param("longitude", row.longitude())
                .param("areaM2", row.areaM2())
                .param("radiusM", row.radiusM())
                .update();
    }
}
