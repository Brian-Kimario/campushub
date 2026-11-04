package com.campushub.skills;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkillKey> {

    @Query("""
            select new com.campushub.skills.OwnSkillRow(s.id, s.name, s.category, us.proficiency,
                us.evidenceNote, us.updatedAt)
            from UserSkill us, Skill s
            where us.key.skillId = s.id
              and us.key.userId = :userId
            order by s.nameKey, s.id
            """)
    List<OwnSkillRow> findOwnSkills(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSkill us where us.key.userId = :userId")
    void deleteForUser(@Param("userId") UUID userId);
}
