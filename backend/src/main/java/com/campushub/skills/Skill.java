package com.campushub.skills;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A catalog entry (DATA_MODEL §8). */
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String name;

    private String nameKey;

    private String category;

    private boolean active;

    protected Skill() {
    }

    public Skill(UUID id, String name, String nameKey, String category) {
        this.id = id;
        this.name = name;
        this.nameKey = nameKey;
        this.category = category;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }
}
