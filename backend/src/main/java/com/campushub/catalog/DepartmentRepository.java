package com.campushub.catalog;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByCode(String code);

    /** A null {@code active} leaves the filter open. */
    @Query("""
            select d from Department d
            where (:active is null or d.active = :active)
            """)
    Page<Department> search(@Param("active") Boolean active, Pageable pageable);

    @Query("select count(u) from UserAccount u where u.departmentId = :id")
    long countUsers(@Param("id") UUID id);

    @Query("select count(c) from Course c where c.departmentId = :id")
    long countCourses(@Param("id") UUID id);
}
