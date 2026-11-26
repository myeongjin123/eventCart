package com.eventcart.payment.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.order.domain.Order;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제 시도 기록. 실패한 시도도 남긴다 (한 주문에 여러 건 가능, 승인은 최대 1건). */
@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 30)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** PG사 거래 번호 (환불할 때 필요). 실패면 null */
    @Column(length = 64)
    private String transactionId;

    /** 실패 사유 */
    @Column(length = 200)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private Payment(Order order, int amount, String method, PaymentStatus status,
                    String transactionId, String failureReason, LocalDateTime requestedAt) {
        this.order = order;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
    }

    public static Payment approved(Order order, String method, String transactionId, LocalDateTime now) {
        return new Payment(order, order.getTotalPrice(), method, PaymentStatus.APPROVED, transactionId, null, now);
    }

    public static Payment failed(Order order, String method, String reason, LocalDateTime now) {
        return new Payment(order, order.getTotalPrice(), method, PaymentStatus.FAILED, null, reason, now);
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
