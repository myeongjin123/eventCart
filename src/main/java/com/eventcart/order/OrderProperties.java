package com.eventcart.order;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param paymentTimeout 주문 후 결제 기한. 지나면 스케줄러가 주문을 만료시키고 재고를 돌려준다.
 */
@ConfigurationProperties("app.order")
public record OrderProperties(Duration paymentTimeout) {

    public OrderProperties {
        if (paymentTimeout == null) {
            paymentTimeout = Duration.ofMinutes(10);
        }
    }
}
