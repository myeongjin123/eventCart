package com.eventcart.event.repository;

import com.eventcart.event.domain.Event;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            select distinct e from Event e
            left join fetch e.eventProducts ep
            left join fetch ep.product
            where e.startAt <= :now and e.endAt > :now
            order by e.startAt
            """)
    List<Event> findOngoing(@Param("now") LocalDateTime now);

    @Query("""
            select e from Event e
            left join fetch e.eventProducts ep
            left join fetch ep.product
            where e.id = :id
            """)
    Optional<Event> findWithProductsById(@Param("id") Long id);
}
