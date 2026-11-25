package com.eventcart.order.stock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param keyPrefix Redis 키 앞부분. DB를 새로 만드는 환경(H2, 테스트)에서는 매번 다른 값을 써서
 *                  이전 실행의 재고 키와 id가 겹치지 않게 한다.
 */
@ConfigurationProperties("app.stock-gate")
public record StockGateProperties(String keyPrefix) {

    public StockGateProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "eventcart";
        }
    }
}
