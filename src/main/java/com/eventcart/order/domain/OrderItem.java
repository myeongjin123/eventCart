package com.eventcart.order.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.event.domain.EventProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_product_id", nullable = false)
    private EventProduct eventProduct;

    @Column(nullable = false)
    private int quantity;

    /** 주문 시점의 이벤트가 스냅샷 */
    @Column(nullable = false)
    private int unitPrice;

    public OrderItem(EventProduct eventProduct, int quantity, int unitPrice) {
        this.eventProduct = eventProduct;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void assignOrder(Order order) {
        this.order = order;
    }

    public int getLineTotal() {
        return unitPrice * quantity;
    }
}
