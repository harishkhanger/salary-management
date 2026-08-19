package com.acme.salary.payroll;

import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.entities.SalaryCredit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * DB contract: the unique constraint (employee_id, year, month) on salary_credits
 * makes double-processing a payroll month structurally impossible.
 */
@DataJpaTest
class PayrollIdempotencyDbContractTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void secondCreditForSameEmployeeAndMonthIsRejectedByUniqueConstraint() {
        CurrencyRate inr = currency("INR", "Indian Rupee", "83.500000");
        Employee employee = em.persist(employee("EMP-0001", inr.getCode()));
        PayrollRun firstRun = em.persist(run(2026, 8));
        PayrollRun retriedRun = em.persist(run(2026, 8));

        em.persistAndFlush(credit(employee.getId(), 2026, 8, firstRun.getId()));

        Throwable thrown = catchThrowable(() ->
                em.persistAndFlush(credit(employee.getId(), 2026, 8, retriedRun.getId())));

        assertThat(thrown).isInstanceOfAny(DataIntegrityViolationException.class,
                JpaSystemException.class, org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void sameMonthForDifferentEmployeesAndDifferentMonthForSameEmployeeAreAllowed() {
        CurrencyRate inr = currency("INR", "Indian Rupee", "83.500000");
        Employee first = em.persist(employee("EMP-0001", inr.getCode()));
        Employee second = em.persist(employee("EMP-0002", inr.getCode()));
        PayrollRun run = em.persist(run(2026, 8));
        PayrollRun nextRun = em.persist(run(2026, 9));

        em.persistAndFlush(credit(first.getId(), 2026, 8, run.getId()));
        em.persistAndFlush(credit(second.getId(), 2026, 8, run.getId()));
        em.persistAndFlush(credit(first.getId(), 2026, 9, nextRun.getId()));
    }

    private CurrencyRate currency(String code, String name, String rate) {
        CurrencyRate currency = new CurrencyRate();
        currency.setCode(code);
        currency.setName(name);
        currency.setUsdRate(new BigDecimal(rate));
        currency.setUpdatedAt(LocalDateTime.now());
        return em.persist(currency);
    }

    private Employee employee(String code, String currencyCode) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setName("Test Person");
        employee.setEmail(code.toLowerCase() + "@acme.test");
        employee.setCountry("India");
        employee.setDepartment("Engineering");
        employee.setCurrencyCode(currencyCode);
        employee.setAnnualSalary(new BigDecimal("1200000.00"));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setJoinedOn(LocalDate.of(2024, 1, 15));
        return employee;
    }

    private PayrollRun run(int year, int month) {
        PayrollRun run = new PayrollRun();
        run.setYear(year);
        run.setMonth(month);
        run.setProcessedCount(0);
        run.setSkippedHeldCount(0);
        run.setAlreadyProcessedCount(0);
        run.setInitiatedBy("hr");
        run.setCreatedAt(LocalDateTime.now());
        return run;
    }

    private SalaryCredit credit(Long employeeId, int year, int month, Long runId) {
        SalaryCredit credit = new SalaryCredit();
        credit.setEmployeeId(employeeId);
        credit.setYear(year);
        credit.setMonth(month);
        credit.setAmount(new BigDecimal("100000.00"));
        credit.setCurrencyCode("INR");
        credit.setUsdRate(new BigDecimal("83.500000"));
        credit.setPayrollRunId(runId);
        credit.setCreatedAt(LocalDateTime.now());
        return credit;
    }
}
