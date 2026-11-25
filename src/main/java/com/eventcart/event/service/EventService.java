package com.eventcart.event.service;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.event.domain.Event;
import com.eventcart.event.domain.EventProduct;
import com.eventcart.event.dto.EventDto;
import com.eventcart.event.repository.EventProductRepository;
import com.eventcart.event.repository.EventRepository;
import com.eventcart.product.domain.Product;
import com.eventcart.product.service.ProductService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventProductRepository eventProductRepository;
    private final ProductService productService;
    private final Clock clock;

    @Transactional
    public EventDto.Response create(EventDto.CreateRequest request) {
        Event event = eventRepository.save(new Event(request.name(), request.startAt(), request.endAt()));
        return EventDto.Response.from(event);
    }

    @Transactional
    public EventDto.EventProductResponse addProduct(Long eventId, EventDto.AddProductRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        Product product = productService.getProduct(request.productId());
        EventProduct eventProduct = eventProductRepository.save(new EventProduct(
                event, product, request.eventPrice(), request.totalQuantity(), request.perMemberLimit()));
        return EventDto.EventProductResponse.from(eventProduct);
    }

    public List<EventDto.Response> findOngoing() {
        return eventRepository.findOngoing(LocalDateTime.now(clock)).stream()
                .map(EventDto.Response::from)
                .toList();
    }

    public EventDto.Response find(Long eventId) {
        return eventRepository.findWithProductsById(eventId)
                .map(EventDto.Response::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    public EventProduct getEventProduct(Long eventProductId) {
        return eventProductRepository.findById(eventProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_PRODUCT_NOT_FOUND));
    }
}
