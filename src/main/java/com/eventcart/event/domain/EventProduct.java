package com.eventcart.event.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이벤트에 등록된 한정 수량 상품. 재고 정합성의 중심 엔티티. */
@Getter
@Entity
@Table(name = "event_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int eventPrice;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Column(nullable = false)
    private int perMemberLimit;

    public EventProduct(Event event, Product product, int eventPrice, int totalQuantity, int perMemberLimit) {
        this.event = event;
        this.product = product;
        this.eventPrice = eventPrice;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.perMemberLimit = perMemberLimit;
        event.addEventProduct(this);
    }

    public void validateOngoing(LocalDateTime now) {
        if (!event.isOngoing(now)) {
            throw new BusinessException(ErrorCode.EVENT_NOT_ONGOING);
        }
    }

    /**
     * 재고를 차감한다. 호출 전 반드시 비관적 락으로 조회한 엔티티여야 한다.
     *
     * @param quantity        이번에 구매할 수량
     * @param alreadyPurchased 회원이 이미 구매한(취소 제외) 수량
     */
    public void purchase(int quantity, int alreadyPurchased) {
        if (alreadyPurchased + quantity > perMemberLimit) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
        if (remainingQuantity < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        remainingQuantity -= quantity;
    }

    public void restore(int quantity) {
        remainingQuantity = Math.min(totalQuantity, remainingQuantity + quantity);
    }
}
