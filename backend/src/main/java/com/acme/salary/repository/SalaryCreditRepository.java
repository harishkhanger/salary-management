package com.acme.salary.repository;

import com.acme.salary.entities.SalaryCredit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Append-only: no update or delete methods are ever declared here.
 */
public interface SalaryCreditRepository extends JpaRepository<SalaryCredit, Long> {

    /** Credit-history panel: an employee's credits, newest period first. */
    @Query("""
            SELECT c FROM SalaryCredit c
            WHERE c.employeeId = :employeeId
            ORDER BY c.year DESC, c.month DESC
            """)
    Page<SalaryCredit> findHistory(@Param("employeeId") Long employeeId, Pageable pageable);

    /**
     * Check-then-insert idempotency + crash resume: employees already
     * credited for the period are skipped and counted, never re-paid.
     * The unique key (employee_id, year, month) remains the referee for races.
     */
    @Query("""
            SELECT c.employeeId FROM SalaryCredit c
            WHERE c.year = :year AND c.month = :month
            """)
    List<Long> findEmployeeIdsCreditedForPeriod(@Param("year") int year, @Param("month") int month);
}
