package com.eventcart.common.web;

/**
 * 1단계 임시 인증: 요청 헤더로 회원을 식별한다. 2단계에서 Spring Security + JWT로 교체한다.
 */
public final class MemberHeader {

    public static final String NAME = "X-Member-Id";

    private MemberHeader() {
    }
}
