package com.eventcart.order.repository;

import com.eventcart.order.domain.OrderItem;
import com.eventcart.order.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 회원이 특정 이벤트 상품을 지금까지 구매한 수량 (주어진 상태의 주문만, 보통 OrderStatus.ACTIVE) */
    @Query("""
            select coalesce(sum(oi.quantity), 0) from OrderItem oi
            where oi.order.member.id = :memberId
              and oi.eventProduct.id = :eventProductId
              and oi.order.status in :statuses
            """)
    long sumQuantity(@Param("memberId") Long memberId,
                     @Param("eventProductId") Long eventProductId,
                     @Param("statuses") Collection<OrderStatus> statuses);

    /** Redis 재고 게이트 초기화용: 이벤트 상품별 회원 누적 구매 수량 */
    @Query("""
            select new com.eventcart.order.repository.BuyerQuantity(oi.order.member.id, sum(oi.quantity))
            from OrderItem oi
            where oi.eventProduct.id = :eventProductId
              and oi.order.status in :statuses
            group by oi.order.member.id
            """)
    List<BuyerQuantity> sumQuantityByMember(@Param("eventProductId") Long eventProductId,
                                            @Param("statuses") Collection<OrderStatus> statuses);
}
