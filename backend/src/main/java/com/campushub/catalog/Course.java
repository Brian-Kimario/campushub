package com.campushub.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Course in the catalog (DATA_MODEL §3.4). */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String code;

    private String title;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID departmentId;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    protected Course() {
    }

    public Course(UUID id, String code, String title, UUID departmentId, Instant now) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.departmentId = departmentId;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, Boolean active, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (active != null) {
            this.active = active;
        }
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public boolean isActive() {
        return active;
    }
}
