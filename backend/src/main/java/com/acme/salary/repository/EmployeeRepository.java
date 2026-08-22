package com.acme.salary.repository;

import com.acme.salary.dto.CurrencyCohortAggregate;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndDeletedFalse(Long id);

    boolean existsByEmployeeCode(String employeeCode);

    /** Bulk-raise cohort: active employees matching the optional filters. */
    @Query("""
            SELECT e.id FROM Employee e
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            """)
    List<Long> findCohortIds(@Param("country") String country,
                             @Param("department") String department);

    /**
     * Bulk-raise preview cost impact: headcount + salary totals per currency,
     * aggregated in the database — the 10k rows are never loaded.
     */
    @Query("""
            SELECT new com.acme.salary.dto.CurrencyCohortAggregate(
                e.currencyCode, COUNT(e.id), SUM(e.annualSalary))
            FROM Employee e
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY e.currencyCode
            """)
    List<CurrencyCohortAggregate> aggregateCohortByCurrency(@Param("country") String country,
                                                            @Param("department") String department);

    /** Payroll: held employees are skipped from processing (holds block payout). */
    @Query("SELECT e.id FROM Employee e WHERE e.deleted = false AND e.status = com.acme.salary.enums.EmployeeStatus.ON_HOLD")
    List<Long> findHeldEmployeeIds();

    long countByDeletedFalseAndStatus(EmployeeStatus status);

    /**
     * Payroll cohort for a period: everyone not deleted who had joined by the
     * end of that month. Someone who joined in July is not owed March —
     * without this rule every past month would look "partially paid" forever.
     */
    @Query("""
            SELECT e.id FROM Employee e
            WHERE e.deleted = false AND e.joinedOn <= :periodEnd
            """)
    List<Long> findPayrollCohortIds(@Param("periodEnd") LocalDate periodEnd);

    /**
     * Payroll screen: ACTIVE employees who had joined by the period's end and
     * have no credit for it — exactly the set a "Pay" for that month would
     * credit (held employees are skipped, so they are not counted as unpaid).
     */
    @Query("""
            SELECT COUNT(e.id) FROM Employee e
            WHERE e.deleted = false
              AND e.status = com.acme.salary.enums.EmployeeStatus.ACTIVE
              AND e.joinedOn <= :periodEnd
              AND NOT EXISTS (
                  SELECT 1 FROM SalaryCredit c
                  WHERE c.employeeId = e.id AND c.year = :year AND c.month = :month)
            """)
    long countActiveUnpaidForPeriod(@Param("year") int year, @Param("month") int month,
                                    @Param("periodEnd") LocalDate periodEnd);
}
