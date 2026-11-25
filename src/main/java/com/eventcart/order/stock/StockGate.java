package com.eventcart.order.stock;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.event.domain.EventProduct;
import com.eventcart.event.domain.EventProductRegistered;
import com.eventcart.event.repository.EventProductRepository;
import com.eventcart.order.domain.OrderStatus;
import com.eventcart.order.repository.BuyerQuantity;
import com.eventcart.order.repository.OrderItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Redis 재고 게이트. DB 락 앞에서 품절·1인 제한 초과 요청을 먼저 돌려보낸다.
 *
 * <ul>
 *   <li>최종 기준은 DB다. 게이트를 통과해도 DB에서 한 번 더 검증한다.</li>
 *   <li>게이트를 통과하는 요청은 남은 재고 수만큼뿐이라 DB 락 경합이 그만큼 줄어든다.</li>
 *   <li>이벤트 상품이 등록되면(커밋 후) 미리 채운다. 첫 주문들이 동시에 초기화하느라 몰리지 않게.</li>
 *   <li>그래도 키가 없으면(Redis 재시작 등) DB 값으로 지연 초기화한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class StockGate {

    private static final RedisScript<Long> RESERVE = script("redis/stock-reserve.lua");
    private static final RedisScript<Long> RELEASE = script("redis/stock-release.lua");
    private static final RedisScript<Long> INIT = script("redis/stock-init.lua");

    private final StringRedisTemplate redis;
    private final EventProductRepository eventProductRepository;
    private final OrderItemRepository orderItemRepository;
    private final String keyPrefix;

    public StockGate(StringRedisTemplate redis, EventProductRepository eventProductRepository,
                     OrderItemRepository orderItemRepository, StockGateProperties properties) {
        this.redis = redis;
        this.eventProductRepository = eventProductRepository;
        this.orderItemRepository = orderItemRepository;
        this.keyPrefix = properties.keyPrefix();
    }

    /** 재고를 예약한다. 실패하면 OUT_OF_STOCK / PURCHASE_LIMIT_EXCEEDED 예외. */
    public void reserve(Long eventProductId, Long memberId, int quantity) {
        long result = run(RESERVE, eventProductId, memberId, quantity);
        if (result == -3) {
            initialize(eventProductId);
            result = run(RESERVE, eventProductId, memberId, quantity);
        }
        if (result == 1) {
            return;
        }
        throw rejection(result, eventProductId, memberId);
    }

    /** 예약을 반납한다. 보상 처리에서 호출되므로 Redis 오류가 나도 원래 예외를 가리지 않게 삼킨다. */
    public void release(Long eventProductId, Long memberId, int quantity) {
        try {
            run(RELEASE, eventProductId, memberId, quantity);
        } catch (RuntimeException e) {
            // 반납에 실패하면 Redis 재고가 실제보다 적게 남는다(덜 팔림). 초과 판매는 DB가 막는다.
            log.error("재고 반납 실패 eventProductId={} memberId={} quantity={}", eventProductId, memberId, quantity, e);
        }
    }

    /** 게이트에 남은 재고 (초기화 전이면 null). 모니터링·테스트용. */
    public Integer remaining(Long eventProductId) {
        Object stock = redis.opsForHash().get(infoKey(eventProductId), "stock");
        return stock == null ? null : Integer.valueOf(stock.toString());
    }

    /** 등록 트랜잭션이 커밋된 뒤 미리 채운다. 실패해도 첫 주문 때 지연 초기화되므로 등록은 성공시킨다. */
    @TransactionalEventListener
    public void onEventProductRegistered(EventProductRegistered event) {
        try {
            initialize(event.eventProductId());
        } catch (RuntimeException e) {
            log.warn("재고 게이트 사전 초기화 실패 eventProductId={}", event.eventProductId(), e);
        }
    }

    /** 게이트 키를 지운다. 다음 요청에서 DB 값으로 다시 초기화된다. (운영 중 재동기화용) */
    public void evict(Long eventProductId) {
        redis.delete(keys(eventProductId));
    }

    public void initialize(Long eventProductId) {
        EventProduct ep = eventProductRepository.findWithProductById(eventProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_PRODUCT_NOT_FOUND));
        List<String> args = new ArrayList<>(List.of(
                String.valueOf(ep.getRemainingQuantity()),
                String.valueOf(ep.getPerMemberLimit()),
                ep.getProduct().getName()));
        for (BuyerQuantity buyer : orderItemRepository.sumQuantityByMember(eventProductId, OrderStatus.ORDERED)) {
            args.add(String.valueOf(buyer.memberId()));
            args.add(String.valueOf(buyer.quantity()));
        }
        Long created = redis.execute(INIT, keys(eventProductId), args.toArray());
        if (Objects.equals(created, 1L)) {
            log.info("재고 게이트 초기화 eventProductId={} stock={}", eventProductId, ep.getRemainingQuantity());
        }
    }

    private BusinessException rejection(long result, Long eventProductId, Long memberId) {
        String infoKey = infoKey(eventProductId);
        String name = String.valueOf(redis.opsForHash().get(infoKey, "name"));
        if (result == -2) {
            Object limit = redis.opsForHash().get(infoKey, "limit");
            Object bought = redis.opsForHash().get(buyersKey(eventProductId), String.valueOf(memberId));
            return new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "%s은(는) 1인당 %s개까지 구매할 수 있습니다. (이미 구매: %s개)"
                            .formatted(name, limit, bought == null ? "0" : bought));
        }
        Object stock = redis.opsForHash().get(infoKey, "stock");
        return new BusinessException(ErrorCode.OUT_OF_STOCK,
                "%s 재고가 부족합니다. (남은 수량: %s개)".formatted(name, stock));
    }

    private long run(RedisScript<Long> script, Long eventProductId, Long memberId, int quantity) {
        Long result = redis.execute(script, keys(eventProductId), String.valueOf(memberId), String.valueOf(quantity));
        return result == null ? 0 : result;
    }

    private List<String> keys(Long eventProductId) {
        return List.of(infoKey(eventProductId), buyersKey(eventProductId));
    }

    /** {...} 해시 태그: Redis Cluster에서도 두 키가 같은 슬롯에 있어야 한 스크립트로 다룰 수 있다. */
    private String infoKey(Long eventProductId) {
        return keyPrefix + ":{event-product:" + eventProductId + "}";
    }

    private String buyersKey(Long eventProductId) {
        return infoKey(eventProductId) + ":buyers";
    }

    private static RedisScript<Long> script(String path) {
        return RedisScript.of(new ClassPathResource(path), Long.class);
    }
}
