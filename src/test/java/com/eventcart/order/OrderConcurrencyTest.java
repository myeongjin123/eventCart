package com.eventcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.service.OrderService;
import com.eventcart.support.TestFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestFixture.class)
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private TestFixture fixture;

    @Test
    void 재고_10개에_100명이_동시에_주문하면_정확히_10명만_성공한다() throws Exception {
        Long eventProductId = fixture.ongoingEventProduct(10, 1);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            members.add(fixture.member());
        }

        Result result = runConcurrently(members, eventProductId);

        assertThat(result.success()).isEqualTo(10);
        assertThat(result.failures()).containsEntry(ErrorCode.OUT_OF_STOCK, 90);
        assertThat(fixture.remaining(eventProductId)).isZero();
    }

    @Test
    void 한_회원이_동시에_여러_번_주문해도_1인_제한을_넘지_않는다() throws Exception {
        Long eventProductId = fixture.ongoingEventProduct(100, 2);
        Long member = fixture.member();
        List<Long> sameMemberTwentyTimes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sameMemberTwentyTimes.add(member);
        }

        Result result = runConcurrently(sameMemberTwentyTimes, eventProductId);

        assertThat(result.success()).isEqualTo(2);
        assertThat(result.failures()).containsEntry(ErrorCode.PURCHASE_LIMIT_EXCEEDED, 18);
        assertThat(fixture.remaining(eventProductId)).isEqualTo(98);
    }

    private Result runConcurrently(List<Long> memberIds, Long eventProductId) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(memberIds.size());
        AtomicInteger success = new AtomicInteger();
        Map<ErrorCode, Integer> failures = new ConcurrentHashMap<>();

        for (Long memberId : memberIds) {
            executor.submit(() -> {
                try {
                    ready.await();
                    orderService.orderDirect(memberId, new OrderDto.DirectRequest(eventProductId, 1));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    failures.merge(e.getErrorCode(), 1, Integer::sum);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
                return null;
            });
        }
        ready.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        return new Result(success.get(), failures);
    }

    private record Result(int success, Map<ErrorCode, Integer> failures) {
    }
}
