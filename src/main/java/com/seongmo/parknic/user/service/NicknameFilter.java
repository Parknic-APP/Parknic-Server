package com.seongmo.parknic.user.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class NicknameFilter {

    private static final Set<String> BANNED_WORDS = Set.of(
            "시발", "씨발", "병신", "개새끼", "fuck", "shit"
    );

    public boolean isInappropriate(String nickname) {
        String lower = nickname.toLowerCase(Locale.ROOT);
        return BANNED_WORDS.stream().anyMatch(lower::contains);
    }
}
