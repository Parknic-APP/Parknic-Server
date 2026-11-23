package com.seongmo.parknic.collection.controller;

import com.seongmo.parknic.collection.dto.CollectionResponse;
import com.seongmo.parknic.collection.service.CollectionService;
import com.seongmo.parknic.common.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping("/api/users/me/collection")
    public CollectionResponse getCollection(@CurrentUserId Long userId) {
        return collectionService.getCollection(userId);
    }
}
