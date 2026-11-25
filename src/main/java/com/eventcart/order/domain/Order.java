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
import jakarta.persistence.Index;
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
@Table(name = "orders",
        indexes = @Index(name = "idx_orders_status_deadline", columnList = "status, payment_deadline"))
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

    /** 이 시각까지 결제하지 않으면 스케줄러가 EXPIRED로 바꾸고 재고를 돌려준다 */
    @Column(nullable = false)
    private LocalDateTime paymentDeadline;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(Member member, LocalDateTime orderedAt, LocalDateTime paymentDeadline) {
        this.member = member;
        this.orderedAt = orderedAt;
        this.paymentDeadline = paymentDeadline;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
        totalPrice += item.getLineTotal();
    }

    /** 결제 가능한지 확인한다. 상태가 결제 대기가 아니거나 기한이 지났으면 예외. */
    public void validatePayable(LocalDateTime now) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (now.isAfter(paymentDeadline)) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
    }

    public void markPaid(LocalDateTime now) {
        validatePayable(now);
        status = OrderStatus.PAID;
    }

    /** 사용자 취소. 결제 대기·결제 완료에서만 가능하다. 반환값: 환불이 필요한지 */
    public boolean cancel() {
        if (!OrderStatus.ACTIVE.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        boolean needsRefund = status == OrderStatus.PAID;
        status = OrderStatus.CANCELED;
        return needsRefund;
    }

    /** 결제 기한 만료. 이미 결제·취소됐으면 false (스케줄러가 조회한 뒤 상태가 바뀐 경우). */
    public boolean expire(LocalDateTime now) {
        if (status != OrderStatus.PENDING_PAYMENT || !now.isAfter(paymentDeadline)) {
            return false;
        }
        status = OrderStatus.EXPIRED;
        return true;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
