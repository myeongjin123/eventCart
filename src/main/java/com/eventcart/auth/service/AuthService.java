package com.eventcart.auth.service;

import com.eventcart.auth.dto.AuthDto;
import com.eventcart.auth.jwt.JwtTokenProvider;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.member.domain.Member;
import com.eventcart.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 이메일이 없을 때와 비밀번호가 틀릴 때 같은 에러를 준다.
     * 다르게 주면 어떤 이메일이 가입돼 있는지 알아낼 수 있다.
     */
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .filter(m -> passwordEncoder.matches(request.password(), m.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String token = tokenProvider.createAccessToken(member.getId(), member.getRole());
        return AuthDto.TokenResponse.bearer(token, tokenProvider.accessTokenValiditySeconds());
    }
}
