package com.eventcart.member.dto;

import com.eventcart.member.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MemberDto {

    private MemberDto() {
    }

    public record CreateRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 50) String name) {
    }

    public record Response(Long id, String email, String name) {

        public static Response from(Member member) {
            return new Response(member.getId(), member.getEmail(), member.getName());
        }
    }
}
