package com.eventcart.event.controller;

import com.eventcart.event.dto.EventDto;
import com.eventcart.event.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto.Response create(@Valid @RequestBody EventDto.CreateRequest request) {
        return eventService.create(request);
    }

    @PostMapping("/{eventId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto.EventProductResponse addProduct(@PathVariable Long eventId,
                                                    @Valid @RequestBody EventDto.AddProductRequest request) {
        return eventService.addProduct(eventId, request);
    }

    @GetMapping("/ongoing")
    public List<EventDto.Response> findOngoing() {
        return eventService.findOngoing();
    }

    @GetMapping("/{eventId}")
    public EventDto.Response find(@PathVariable Long eventId) {
        return eventService.find(eventId);
    }
}
