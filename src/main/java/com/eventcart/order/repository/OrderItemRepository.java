package com.eventcart.order.repository;

import com.eventcart.order.domain.OrderItem;
import com.eventcart.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 회원이 특정 이벤트 상품을 지금까지 구매한 수량 (주어진 상태의 주문만) */
    @Query("""
            select coalesce(sum(oi.quantity), 0) from OrderItem oi
            where oi.order.member.id = :memberId
              and oi.eventProduct.id = :eventProductId
              and oi.order.status = :status
            """)
    long sumQuantity(@Param("memberId") Long memberId,
                     @Param("eventProductId") Long eventProductId,
                     @Param("status") OrderStatus status);
}
