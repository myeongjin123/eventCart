package com.eventcart.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 통합 테스트 공통 설정. 모든 통합 테스트가 이 어노테이션 하나만 쓰면
 * 스프링 컨텍스트(와 MySQL·Redis 컨테이너)를 한 번만 띄우고 같이 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestFixture.class})
public @interface IntegrationTest {
}
