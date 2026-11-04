package com.campushub.catalog;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    boolean existsByCode(String code);

    /** Null arguments leave their filter open. */
    @Query("""
            select c from Course c
            where (:departmentId is null or c.departmentId = :departmentId)
              and (:active is null or c.active = :active)
            """)
    Page<Course> search(@Param("departmentId") UUID departmentId, @Param("active") Boolean active,
                        Pageable pageable);

    @Query("select count(p) from Project p where p.courseId = :id")
    long countProjects(@Param("id") UUID id);
}
