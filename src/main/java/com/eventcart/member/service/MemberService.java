package com.eventcart.member.service;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.member.domain.Member;
import com.eventcart.member.domain.Role;
import com.eventcart.member.dto.MemberDto;
import com.eventcart.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /** 회원가입. 가입 경로로는 항상 USER 권한만 만들어진다. */
    @Transactional
    public MemberDto.Response create(MemberDto.CreateRequest request) {
        return MemberDto.Response.from(register(request.email(), request.name(), request.password(), Role.USER));
    }

    @Transactional
    public Member register(String email, String name, String rawPassword, Role role) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        return memberRepository.save(new Member(email, name, passwordEncoder.encode(rawPassword), role));
    }

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
