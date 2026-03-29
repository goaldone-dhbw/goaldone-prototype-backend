package de.goaldone.backend.entity;

import de.goaldone.backend.model.RecurringExceptionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "recurring_exceptions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "occurrence_date"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RecurringTemplate template;

    @Column(nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringExceptionType type;

    private LocalDate newDate;

    private LocalTime newStartTime;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
