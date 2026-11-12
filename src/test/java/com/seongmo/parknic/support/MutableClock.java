package com.seongmo.parknic.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** 테스트에서 시각을 고정하거나 앞으로 옮길 수 있는 Clock. 스케줄러 스레드에서도 안전하다. */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** 그 시점의 복사본을 돌려준다. 이후 원본의 set/advance와는 연동되지 않는다. */
    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now.get(), zone);
    }
}
