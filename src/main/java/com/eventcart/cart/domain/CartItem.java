package com.eventcart.cart.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.event.domain.EventProduct;
import com.eventcart.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cart_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_member_event_product",
                columnNames = {"member_id", "event_product_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_product_id", nullable = false)
    private EventProduct eventProduct;

    @Column(nullable = false)
    private int quantity;

    public CartItem(Member member, EventProduct eventProduct, int quantity) {
        this.member = member;
        this.eventProduct = eventProduct;
        this.quantity = quantity;
    }

    public void addQuantity(int quantity) {
        changeQuantity(this.quantity + quantity);
    }

    /** 장바구니 단계에서는 1인 제한만 검사한다. 재고는 주문 시점에 확인한다. */
    public void changeQuantity(int quantity) {
        if (quantity > eventProduct.getPerMemberLimit()) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
        this.quantity = quantity;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
