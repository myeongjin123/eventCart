package com.eventcart.event.domain;

import com.eventcart.common.domain.BaseTimeEntity;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @OneToMany(mappedBy = "event")
    private List<EventProduct> eventProducts = new ArrayList<>();

    public Event(String name, LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_PERIOD);
        }
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** 시작 시각 포함, 종료 시각 미포함 */
    public boolean isOngoing(LocalDateTime now) {
        return !now.isBefore(startAt) && now.isBefore(endAt);
    }

    void addEventProduct(EventProduct eventProduct) {
        eventProducts.add(eventProduct);
    }
}
