package com.seongmo.parknic.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.seongmo.parknic.support.IntegrationTest;

class SchemaConstraintIT extends IntegrationTest {

    @Test
    void 시드_데이터가_들어있다() {
        assertThat(count("characters")).isEqualTo(8);
        assertThat(count("achievements")).isEqualTo(6);
    }

    @Test
    void 진행_중_세션은_사용자당_1개만_가능하다() {
        long userId = insertUser("a1");
        long parkId = insertPark("P1");
        insertSession(userId, parkId, "IN_PROGRESS");

        assertThatThrownBy(() -> insertSession(userId, parkId, "IN_PROGRESS"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_checkin_in_progress");
    }

    @Test
    void 끝난_세션이_여러_개여도_진행_중_세션을_만들_수_있다() {
        long userId = insertUser("a1");
        long parkId = insertPark("P1");
        insertSession(userId, parkId, "COMPLETED");
        insertSession(userId, parkId, "FAILED");

        insertSession(userId, parkId, "IN_PROGRESS");

        assertThat(count("checkin_sessions")).isEqualTo(3);
    }

    @Test
    void 다른_사용자는_각자_진행_중_세션을_가질_수_있다() {
        long parkId = insertPark("P1");
        insertSession(insertUser("a1"), parkId, "IN_PROGRESS");

        insertSession(insertUser("a2"), parkId, "IN_PROGRESS");

        assertThat(count("checkin_sessions")).isEqualTo(2);
    }

    @Test
    void 같은_공원_같은_날_배지는_1개만_가능하다() {
        long userId = insertUser("a1");
        long parkId = insertPark("P1");
        LocalDate day = LocalDate.of(2026, 11, 6);
        insertBadge(userId, parkId, day);

        assertThatThrownBy(() -> insertBadge(userId, parkId, day))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_badges_user_park_date");
    }

    @Test
    void 다른_날이나_다른_공원이면_배지를_또_받을_수_있다() {
        long userId = insertUser("a1");
        long parkId = insertPark("P1");
        LocalDate day = LocalDate.of(2026, 11, 6);
        insertBadge(userId, parkId, day);

        insertBadge(userId, parkId, day.plusDays(1));
        insertBadge(userId, insertPark("P2"), day);

        assertThat(count("badges")).isEqualTo(3);
    }

    @Test
    void 닉네임은_중복될_수_없지만_NULL은_여러_명이_가질_수_있다() {
        insertUser("a1");
        insertUser("a2");   // 둘 다 닉네임 NULL (PENDING 상태 가정): 충돌 없음
        setNickname("a1", "산책왕");

        assertThatThrownBy(() -> setNickname("a2", "산책왕"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_users_nickname");
    }

    // ----- 헬퍼 -----

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private long insertUser(String providerId) {
        return jdbc.sql("""
                        INSERT INTO users (provider, provider_id, status, created_at)
                        VALUES ('APPLE', :pid, 'ACTIVE', :now) RETURNING id
                        """)
                .param("pid", providerId)
                .param("now", now())
                .query(Long.class)
                .single();
    }

    private void setNickname(String providerId, String nickname) {
        jdbc.sql("UPDATE users SET nickname = :nickname WHERE provider_id = :pid")
                .param("nickname", nickname)
                .param("pid", providerId)
                .update();
    }

    private long insertPark(String manageNo) {
        return jdbc.sql("""
                        INSERT INTO parks (manage_no, name, park_type, latitude, longitude, radius_m)
                        VALUES (:no, '테스트공원', 'SMALL', 37.5, 127.0, 100) RETURNING id
                        """)
                .param("no", manageNo)
                .query(Long.class)
                .single();
    }

    private void insertSession(long userId, long parkId, String status) {
        jdbc.sql("""
                        INSERT INTO checkin_sessions (user_id, park_id, status, started_at)
                        VALUES (:userId, :parkId, :status, :now)
                        """)
                .param("userId", userId)
                .param("parkId", parkId)
                .param("status", status)
                .param("now", now())
                .update();
    }

    private void insertBadge(long userId, long parkId, LocalDate day) {
        jdbc.sql("""
                        INSERT INTO badges (user_id, park_id, earned_date, earned_at)
                        VALUES (:userId, :parkId, :day, :now)
                        """)
                .param("userId", userId)
                .param("parkId", parkId)
                .param("day", day)
                .param("now", now())
                .update();
    }
}
