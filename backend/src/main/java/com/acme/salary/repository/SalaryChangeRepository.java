package com.acme.salary.repository;

import com.acme.salary.dto.response.RecentlyRaisedEmployee;
import com.acme.salary.entities.SalaryChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Append-only: no update or delete methods are ever declared here.
 */
public interface SalaryChangeRepository extends JpaRepository<SalaryChange, Long> {

    /** Change-history panel: an employee's changes, newest first. */
    @Query("""
            SELECT c FROM SalaryChange c
            WHERE c.employeeId = :employeeId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    Page<SalaryChange> findHistory(@Param("employeeId") Long employeeId, Pageable pageable);

    /**
     * Guardrail baseline: the earliest change inside the trailing-12-month
     * window — its old_salary is the salary the window started with.
     */
    @Query("""
            SELECT c FROM SalaryChange c
            WHERE c.employeeId = :employeeId AND c.createdAt >= :windowStart
            ORDER BY c.createdAt ASC, c.id ASC
            LIMIT 1
            """)
    Optional<SalaryChange> findEarliestChangeInWindow(@Param("employeeId") Long employeeId,
                                                      @Param("windowStart") LocalDateTime windowStart);

    /**
     * Bulk-raise preview: cohort employees who already received a change
     * inside the recently-raised window, flagged for optional exclusion.
     */
    @Query("""
            SELECT new com.acme.salary.dto.response.RecentlyRaisedEmployee(
                e.id, e.employeeCode, e.name, MAX(c.createdAt))
            FROM SalaryChange c JOIN Employee e ON e.id = c.employeeId
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
              AND c.createdAt >= :cutoff
            GROUP BY e.id, e.employeeCode, e.name
            ORDER BY MAX(c.createdAt) DESC
            """)
    List<RecentlyRaisedEmployee> findRecentlyRaised(@Param("country") String country,
                                                    @Param("department") String department,
                                                    @Param("cutoff") LocalDateTime cutoff);

    /** Resume support: employees this bulk run has already applied a change to. */
    @Query("SELECT c.employeeId FROM SalaryChange c WHERE c.bulkRaiseRunId = :runId")
    List<Long> findEmployeeIdsByRun(@Param("runId") Long runId);
}
