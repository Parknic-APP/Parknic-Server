package com.seongmo.parknic.checkin.scheduler;

import com.seongmo.parknic.checkin.service.CheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckinScheduler {

    private final CheckinService checkinService;

    // 매일 00:00(KST)에 어제까지 시작한 진행 중 체크인을 실패 처리
    @Scheduled(cron = "${parknic.checkin.midnight-cron:0 0 0 * * *}", zone = "Asia/Seoul")
    public void failExpiredAtMidnight() {
        failExpired();
    }

    // 서버가 자정에 꺼져 있었으면 cron이 그 자정을 놓치므로, 시작 직후에도 한 번 정리
    @EventListener(ApplicationReadyEvent.class)
    public void failExpiredOnStartup() {
        failExpired();
    }

    private void failExpired() {
        try {
            checkinService.failExpiredSessions();
        } catch (Exception e) {
            log.error("자정 체크인 정리 실패", e);
        }
    }
}
