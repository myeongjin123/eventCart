package com.eventcart.auth.config;

import com.eventcart.member.domain.Role;
import com.eventcart.member.repository.MemberRepository;
import com.eventcart.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 회원가입 API로는 USER만 만들어지므로, 첫 관리자는 설정값(app.admin.*)으로 만든다.
 * 이미 있으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.isConfigured() || memberRepository.existsByEmail(adminProperties.email())) {
            return;
        }
        memberService.register(adminProperties.email(), "관리자", adminProperties.password(), Role.ADMIN);
        log.info("관리자 계정 생성: {}", adminProperties.email());
    }
}
