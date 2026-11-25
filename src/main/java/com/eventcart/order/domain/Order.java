package com.eventcart.order.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.member.domain.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private int totalPrice;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(Member member, LocalDateTime orderedAt) {
        this.member = member;
        this.orderedAt = orderedAt;
        this.status = OrderStatus.ORDERED;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
        totalPrice += item.getLineTotal();
    }

    public void cancel() {
        if (status != OrderStatus.ORDERED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        status = OrderStatus.CANCELED;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
