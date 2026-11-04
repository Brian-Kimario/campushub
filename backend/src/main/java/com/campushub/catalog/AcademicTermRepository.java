package com.campushub.catalog;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {

    boolean existsByCode(String code);

    /** A null {@code status} leaves the filter open. */
    @Query("""
            select t from AcademicTerm t
            where (:status is null or t.status = :status)
            """)
    Page<AcademicTerm> search(@Param("status") TermStatus status, Pageable pageable);

    @Query("select count(p) from Project p where p.termId = :id")
    long countProjects(@Param("id") UUID id);
}
