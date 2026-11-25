package com.eventcart.cart.repository;

import com.eventcart.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select ci from CartItem ci
            join fetch ci.eventProduct ep
            join fetch ep.product
            join fetch ep.event
            where ci.member.id = :memberId
            order by ci.id
            """)
    List<CartItem> findAllWithProductByMemberId(@Param("memberId") Long memberId);

    Optional<CartItem> findByMemberIdAndEventProductId(Long memberId, Long eventProductId);

    /**
     * 주문용: 엔티티를 영속성 컨텍스트에 올리지 않고 (이벤트상품 id, 수량)만 조회한다.
     * EventProduct를 미리 로딩하면 이후 FOR UPDATE 조회가 1차 캐시의 오래된 재고 값을 돌려줄 수 있다.
     */
    @Query("""
            select new com.eventcart.cart.repository.CartLine(ci.eventProduct.id, ci.quantity)
            from CartItem ci
            where ci.member.id = :memberId
            """)
    List<CartLine> findLinesByMemberId(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem ci where ci.member.id = :memberId and ci.eventProduct.id in :eventProductIds")
    int deleteByMemberIdAndEventProductIds(@Param("memberId") Long memberId,
                                           @Param("eventProductIds") Collection<Long> eventProductIds);
}
