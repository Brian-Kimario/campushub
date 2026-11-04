package com.campushub.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Academic department (DATA_MODEL §3.2). Referenced rows are deactivated rather than deleted (FR-CAT-03). */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String code;

    private String name;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    protected Department() {
    }

    public Department(UUID id, String code, String name, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, Boolean active, Instant now) {
        if (name != null) {
            this.name = name;
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

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
