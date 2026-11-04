package com.campushub.skills;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    boolean existsByNameKey(String nameKey);

    @Query("""
            select s from Skill s
            where s.active = true
              and (:category is null or s.category = :category)
            """)
    Page<Skill> search(@Param("category") String category, Pageable pageable);
}
