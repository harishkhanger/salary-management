package com.acme.salary.repository;

import com.acme.salary.dto.OverThresholdAggregate;
import com.acme.salary.entities.SalaryChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
     * Bulk-raise preview: cohort employees whose raises total more than the
     * guardrail threshold over their whole history — current salary against
     * the old_salary of their first recorded change (MIN(id): the ledger is
     * append-only, so id order is time order). Heaviest first. Computed in
     * the database; the comparison is kept multiplicative to avoid division.
     */
    @Query("""
            SELECT new com.acme.salary.dto.OverThresholdAggregate(
                e.id, e.employeeCode, e.name, e.annualSalary, f.oldSalary,
                (SELECT MAX(c.createdAt) FROM SalaryChange c WHERE c.employeeId = e.id))
            FROM SalaryChange f JOIN Employee e ON e.id = f.employeeId
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
              AND f.id = (SELECT MIN(c2.id) FROM SalaryChange c2 WHERE c2.employeeId = e.id)
              AND e.annualSalary * 100 > f.oldSalary * (100 + :thresholdPercent)
            ORDER BY e.annualSalary / f.oldSalary DESC
            """)
    List<OverThresholdAggregate> findOverThreshold(@Param("country") String country,
                                                   @Param("department") String department,
                                                   @Param("thresholdPercent") BigDecimal thresholdPercent);

    /** Resume support: employees this bulk run has already applied a change to. */
    @Query("SELECT c.employeeId FROM SalaryChange c WHERE c.bulkRaiseRunId = :runId")
    List<Long> findEmployeeIdsByRun(@Param("runId") Long runId);
}
