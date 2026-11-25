package com.eventcart.member.dto;

import com.eventcart.member.domain.Member;
import com.eventcart.member.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MemberDto {

    private MemberDto() {
    }

    public record CreateRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Size(min = 8, max = 64) String password) {

        /** 로그에 비밀번호가 찍히지 않도록 toString에서 제외한다. */
        @Override
        public String toString() {
            return "CreateRequest[email=" + email + ", name=" + name + "]";
        }
    }

    public record Response(Long id, String email, String name, Role role) {

        public static Response from(Member member) {
            return new Response(member.getId(), member.getEmail(), member.getName(), member.getRole());
        }
    }
}
