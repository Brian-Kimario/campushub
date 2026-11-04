package com.campushub.catalog;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Academic term (DATA_MODEL §3.3). Terms are deactivated with status INACTIVE, never deleted while referenced. */
@Entity
@Table(name = "academic_terms")
public class AcademicTerm {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String code;

    private String name;

    private LocalDate startsOn;

    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    private TermStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    protected AcademicTerm() {
    }

    public AcademicTerm(UUID id, String code, String name, LocalDate startsOn, LocalDate endsOn,
                        TermStatus status, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, LocalDate startsOn, LocalDate endsOn, TermStatus status, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (startsOn != null) {
            this.startsOn = startsOn;
        }
        if (endsOn != null) {
            this.endsOn = endsOn;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public TermStatus getStatus() {
        return status;
    }
}
