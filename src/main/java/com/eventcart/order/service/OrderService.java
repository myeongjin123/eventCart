package com.eventcart.order.service;

import com.eventcart.cart.repository.CartItemRepository;
import com.eventcart.cart.repository.CartLine;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.event.domain.EventProduct;
import com.eventcart.event.repository.EventProductRepository;
import com.eventcart.member.domain.Member;
import com.eventcart.member.service.MemberService;
import com.eventcart.order.domain.Order;
import com.eventcart.order.domain.OrderItem;
import com.eventcart.order.domain.OrderStatus;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.repository.OrderItemRepository;
import com.eventcart.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 = 재고 차감. 동시성 전략(1단계): 이벤트 상품 행 비관적 락.
 *
 * <p>READ_COMMITTED를 쓰는 이유: MySQL 기본값인 REPEATABLE_READ에서는 락을 기다린 뒤 실행하는
 * 일반 SELECT(회원 누적 구매 수량)가 트랜잭션 초기 스냅샷을 읽어, 같은 회원의 동시 주문이
 * 1인 구매 제한을 통과할 수 있다. READ_COMMITTED는 매 조회마다 최신 커밋을 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventProductRepository eventProductRepository;
    private final CartItemRepository cartItemRepository;
    private final MemberService memberService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDto.Response orderDirect(Long memberId, OrderDto.DirectRequest request) {
        Member member = memberService.getMember(memberId);
        Order order = placeOrder(member, Map.of(request.eventProductId(), request.quantity()));
        return OrderDto.Response.from(order);
    }

    /** 장바구니 전체를 한 트랜잭션으로 주문한다. 하나라도 실패하면 전체 롤백. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDto.Response checkout(Long memberId) {
        Member member = memberService.getMember(memberId);
        List<CartLine> lines = cartItemRepository.findLinesByMemberId(memberId);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        lines.forEach(line -> quantities.merge(line.eventProductId(), line.quantity(), Integer::sum));

        Order order = placeOrder(member, quantities);
        OrderDto.Response response = OrderDto.Response.from(order);
        cartItemRepository.deleteByMemberIdAndEventProductIds(memberId, quantities.keySet());
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDto.Response cancel(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.isOwnedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel();

        Map<Long, Integer> quantities = new TreeMap<>();
        order.getItems().forEach(item -> quantities.merge(item.getEventProduct().getId(), item.getQuantity(), Integer::sum));
        List<EventProduct> locked = eventProductRepository.findAllByIdInForUpdate(quantities.keySet());
        locked.forEach(ep -> ep.restore(quantities.get(ep.getId())));

        return OrderDto.Response.from(order);
    }

    public List<OrderDto.Response> findMyOrders(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
                .map(OrderDto.Response::from)
                .toList();
    }

    public OrderDto.Response find(Long memberId, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.isOwnedBy(memberId))
                .map(OrderDto.Response::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * @param quantities eventProductId → 수량
     */
    private Order placeOrder(Member member, Map<Long, Integer> quantities) {
        // 1) 이벤트 상품 행을 id 오름차순으로 잠근다 (SELECT ... FOR UPDATE)
        List<EventProduct> locked = eventProductRepository.findAllByIdInForUpdate(quantities.keySet());
        if (locked.size() != quantities.size()) {
            throw new BusinessException(ErrorCode.EVENT_PRODUCT_NOT_FOUND);
        }

        // 2) 락을 잡은 상태에서 기간·1인 제한·재고를 검증하고 차감한다
        LocalDateTime now = LocalDateTime.now(clock);
        Order order = new Order(member, now);
        for (EventProduct ep : locked) {
            int quantity = quantities.get(ep.getId());
            ep.validateOngoing(now);
            int alreadyPurchased = (int) orderItemRepository.sumQuantity(member.getId(), ep.getId(), OrderStatus.ORDERED);
            ep.purchase(quantity, alreadyPurchased);
            order.addItem(new OrderItem(ep, quantity, ep.getEventPrice()));
        }
        return orderRepository.save(order);
    }
}
