package com.seongmo.parknic.park.service;

import com.seongmo.parknic.park.dto.ParkImportRow;
import com.seongmo.parknic.park.entity.ParkType;
import com.seongmo.parknic.park.repository.ParkImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty("parknic.park-import.file")
@RequiredArgsConstructor
public class ParkImporter implements ApplicationRunner {

    private static final Charset CSV_CHARSET = Charset.forName("x-windows-949");
    private static final int MIN_RADIUS_M = 50;
    private static final int MAX_RADIUS_M = 500;

    private final ParkImportRepository parkImportRepository;

    @Value("${parknic.park-import.file}")
    private String filePath;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        int read = 0;
        int imported = 0;
        int skipped = 0;
        Set<String> keys = new HashSet<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        try (Reader reader = Files.newBufferedReader(Path.of(filePath), CSV_CHARSET);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                read++;
                Optional<ParkImportRow> row = toRow(record);
                if (row.isEmpty()) {
                    skipped++;
                    continue;
                }
                parkImportRepository.upsert(row.get());
                keys.add(row.get().manageNo());
                imported++;
            }
        }
        log.info("park import: 읽음 {} / 적재 {} / 건너뜀 {} / 합쳐짐 {} / 최종 {}",
                read, imported, skipped, imported - keys.size(), keys.size());
    }

    private Optional<ParkImportRow> toRow(CSVRecord record) {
        Optional<ParkType> type = ParkType.fromKorean(record.get("공원구분"));
        Double latitude = parseDouble(record.get("위도"));
        Double longitude = parseDouble(record.get("경도"));
        if (type.isEmpty() || latitude == null || longitude == null || !isInKorea(latitude, longitude)) {
            return Optional.empty();
        }

        String name = record.get("공원명").trim();
        String manageNo = record.get("관리번호").trim() + "|" + name;
        String roadAddress = record.get("소재지도로명주소").trim();
        String address = roadAddress.isEmpty() ? record.get("소재지지번주소").trim() : roadAddress;
        Double parsedArea = parseDouble(record.get("공원면적"));
        double areaM2 = parsedArea == null ? 0 : parsedArea;

        return Optional.of(new ParkImportRow(
                manageNo, name, type.get(), address, latitude, longitude, areaM2, calcRadius(areaM2)));
    }

    static int calcRadius(double areaM2) {
        int radius = (int) Math.round(Math.sqrt(areaM2 / Math.PI));
        return Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, radius));
    }

    private static boolean isInKorea(double latitude, double longitude) {
        return latitude >= 33 && latitude <= 39 && longitude >= 124 && longitude <= 132;
    }

    private static Double parseDouble(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
