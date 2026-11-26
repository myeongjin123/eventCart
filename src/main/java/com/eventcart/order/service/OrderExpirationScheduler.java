package com.eventcart.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 결제 기한이 지난 주문을 주기적으로 만료시켜 재고를 돌려준다.
 *
 * <p>서버가 여러 대면 모든 서버가 같이 돈다. 주문 행 락 + 상태 재확인 덕분에 같은 주문이 두 번
 * 만료되지는 않지만 일을 중복으로 한다. 서버를 늘릴 때는 ShedLock 등으로 한 대만 돌게 하는 것이 좋다.
 *
 * <p>테스트에서는 app.order.expiration.enabled=false로 끄고 {@link OrderFacade#expireOverdue}를 직접 호출한다.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.order.expiration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {

    private static final int BATCH_SIZE = 100;

    private final OrderFacade orderFacade;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.order.expiration.interval:PT30S}")
    public void expireOverdueOrders() {
        int expired = orderFacade.expireOverdue(LocalDateTime.now(clock), BATCH_SIZE);
        if (expired > 0) {
            log.info("결제 기한 만료 주문 {}건 처리", expired);
        }
    }
}
