package com.eventcart.order.repository;

import com.eventcart.order.domain.Order;
import com.eventcart.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByMemberIdOrderByIdDesc(Long memberId);

    /** 중복 취소로 재고가 두 번 복구되지 않도록 주문 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** 결제 기한이 지난 결제 대기 주문 (만료 스케줄러용, 인덱스: status, payment_deadline) */
    @Query("""
            select o.id from Order o
            where o.status = :status and o.paymentDeadline < :now
            order by o.id
            """)
    List<Long> findIdsByStatusAndDeadlineBefore(@Param("status") OrderStatus status,
                                                @Param("now") LocalDateTime now,
                                                Pageable pageable);
}
