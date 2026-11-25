package com.eventcart.event.repository;

import com.eventcart.event.domain.EventProduct;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventProductRepository extends JpaRepository<EventProduct, Long> {

    /**
     * SELECT ... FOR UPDATE. id 오름차순으로 락을 잡아 여러 상품 동시 주문 시 데드락을 방지한다.
     * event를 join fetch하지 않는다: 조인하면 event 행까지 잠겨 같은 이벤트의 모든 주문이 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ep from EventProduct ep
            where ep.id in :ids
            order by ep.id
            """)
    List<EventProduct> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select ep from EventProduct ep join fetch ep.product where ep.id = :id")
    Optional<EventProduct> findWithProductById(@Param("id") Long id);
}
