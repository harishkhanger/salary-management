package com.acme.salary.repository;

import com.acme.salary.dto.CurrencyCohortAggregate;
import com.acme.salary.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
