package com.eventcart.payment.repository;

import com.eventcart.payment.domain.Payment;
import com.eventcart.payment.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findAllByOrderIdOrderByIdAsc(Long orderId);
}
