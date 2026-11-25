package com.eventcart.order.service;

import com.eventcart.cart.repository.CartItemRepository;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.stock.StockGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 진입점 (4단계). 트랜잭션 밖에서 Redis 예약 → DB 주문 → 실패하면 예약 반납(보상).
 *
 * <pre>
 *  요청 ─▶ Redis 예약 ──실패──▶ 즉시 409 (DB 접근 없음)
 *              │
 *             성공
 *              ▼
 *          DB 주문 (비관적 락, 최종 검증) ──실패──▶ Redis 반납 후 예외
 *              │
 *             성공 ─▶ 201
 * </pre>
 *
 * <p>일부러 @Transactional을 붙이지 않는다. DB 트랜잭션이 끝난 뒤(롤백 포함)에 반납해야
 * 반납한 재고를 다른 요청이 가져가도 DB와 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final StockGate stockGate;
    private final CartItemRepository cartItemRepository;

    public OrderDto.Response orderDirect(Long memberId, OrderDto.DirectRequest request) {
        Map<Long, Integer> quantities = Map.of(request.eventProductId(), request.quantity());
        return withReservation(memberId, quantities, () -> orderService.orderDirect(memberId, request));
    }

    public OrderDto.Response checkout(Long memberId) {
        Map<Long, Integer> quantities = new TreeMap<>();
        cartItemRepository.findLinesByMemberId(memberId)
                .forEach(line -> quantities.merge(line.eventProductId(), line.quantity(), Integer::sum));
        if (quantities.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        return withReservation(memberId, quantities, () -> orderService.checkout(memberId, quantities));
    }

    /** DB에서 취소가 확정된 뒤에만 Redis 재고를 돌려준다. */
    public OrderDto.Response cancel(Long memberId, Long orderId) {
        OrderDto.Response canceled = orderService.cancel(memberId, orderId);
        canceled.items().forEach(item -> stockGate.release(item.eventProductId(), memberId, item.quantity()));
        return canceled;
    }

    private OrderDto.Response withReservation(Long memberId, Map<Long, Integer> quantities,
                                              Supplier<OrderDto.Response> order) {
        List<Map.Entry<Long, Integer>> reserved = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
                stockGate.reserve(entry.getKey(), memberId, entry.getValue());
                reserved.add(entry);
            }
            return order.get();
        } catch (RuntimeException e) {
            reserved.forEach(r -> stockGate.release(r.getKey(), memberId, r.getValue()));
            throw e;
        }
    }
}
