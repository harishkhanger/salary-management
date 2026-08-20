package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.request.EmployeeCreateRequest;
import com.acme.salary.dto.request.EmployeeStatusRequest;
import com.acme.salary.dto.response.EmployeeResponse;
import com.acme.salary.dto.request.EmployeeUpdateRequest;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private EmployeeService service;

    private static final LocalDate JOINED = LocalDate.of(2024, 3, 1);

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employeeRepository, currencyRateRepository,
                new PaginationProperties(100), eventPublisher);
    }

    private EmployeeCreateRequest createRequest(String code) {
        return new EmployeeCreateRequest(code, "Asha Rao", "asha@acme.test", "India",
                "Engineering", "INR", new BigDecimal("1200000.00"), JOINED);
    }

    private Employee existingEmployee(long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-00007");
        e.setName("Asha Rao");
        e.setEmail("asha@acme.test");
        e.setCountry("India");
        e.setDepartment("Engineering");
        e.setCurrencyCode("INR");
        e.setAnnualSalary(new BigDecimal("1200000.00"));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setJoinedOn(JOINED);
        e.setVersion(3);
        return e;
    }

    private void stubCurrencyExists(String code) {
        when(currencyRateRepository.existsById(code)).thenReturn(true);
    }

    // --- create ---

    @Test
    void createPersistsActiveEmployeeWithSuppliedCode() {
        stubCurrencyExists("INR");
        when(employeeRepository.existsByEmployeeCode("EMP-90001")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        EmployeeResponse response = service.create(createRequest("EMP-90001"));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.employeeCode()).isEqualTo("EMP-90001");
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void createGeneratesCodeFromIdWhenNoneSupplied() {
        stubCurrencyExists("INR");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse response = service.create(createRequest(null));

        assertThat(response.employeeCode()).isEqualTo("EMP-00042");
        verify(employeeRepository).saveAndFlush(any(Employee.class));
        verify(employeeRepository, never()).existsByEmployeeCode(anyString());
    }

    @Test
    void createRejectsDuplicateEmployeeCode() {
        stubCurrencyExists("INR");
        when(employeeRepository.existsByEmployeeCode("EMP-90001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("EMP-90001")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EMP-90001");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownCurrency() {
        when(currencyRateRepository.existsById("INR")).thenReturn(false);

        assertThatThrownBy(() -> service.create(createRequest("EMP-90001")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INR");
        verify(employeeRepository, never()).save(any());
    }

    // --- read ---

    @Test
    void getByIdReturnsEmployee() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existingEmployee(7L)));

        assertThat(service.getById(7L).employeeCode()).isEqualTo("EMP-00007");
    }

    @Test
    void getByIdThrowsNotFoundForMissingOrDeleted() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(7L)).isInstanceOf(NotFoundException.class);
    }

    // --- update ---

    @Test
    void updateAppliesProfileFieldsButNeverSalaryOrStatus() {
        Employee existing = existingEmployee(7L);
        stubCurrencyExists("EUR");
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existing));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeUpdateRequest request = new EmployeeUpdateRequest("Asha R.", "asha.r@acme.test",
                "Germany", "Platform", "EUR", JOINED, 3);
        EmployeeResponse response = service.update(7L, request);

        assertThat(response.name()).isEqualTo("Asha R.");
        assertThat(response.country()).isEqualTo("Germany");
        assertThat(response.currencyCode()).isEqualTo("EUR");
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAnnualSalary()).isEqualByComparingTo("1200000.00");
        assertThat(saved.getValue().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void updateRejectsStaleVersion() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existingEmployee(7L)));

        EmployeeUpdateRequest stale = new EmployeeUpdateRequest("Asha R.", "asha.r@acme.test",
                "Germany", "Platform", "INR", JOINED, 2);

        assertThatThrownBy(() -> service.update(7L, stale))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("version");
        verify(employeeRepository, never()).save(any());
    }

    // --- hold / release ---

    @Test
    void changeStatusPutsEmployeeOnHold() {
        Employee existing = existingEmployee(7L);
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existing));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeResponse response = service.changeStatus(7L,
                new EmployeeStatusRequest(EmployeeStatus.ON_HOLD, 3));

        assertThat(response.status()).isEqualTo(EmployeeStatus.ON_HOLD);
    }

    @Test
    void changeStatusRejectsStaleVersion() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existingEmployee(7L)));

        assertThatThrownBy(() -> service.changeStatus(7L,
                new EmployeeStatusRequest(EmployeeStatus.ON_HOLD, 2)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("STALE_VERSION");
        verify(employeeRepository, never()).saveAndFlush(any());
    }

    // --- soft delete ---

    @Test
    void softDeleteFlagsRowInsteadOfRemovingIt() {
        Employee existing = existingEmployee(7L);
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L);

        assertThat(existing.isDeleted()).isTrue();
        verify(employeeRepository).save(existing);
        verify(employeeRepository, never()).delete(any(Employee.class));
        verify(employeeRepository, never()).deleteById(any());
    }

    // --- directory ---

    @Test
    void directoryReturnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(employeeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingEmployee(7L)), pageable, 1));

        PageResponse<EmployeeResponse> page =
                service.directory(0, 20, "asha", "India", "Engineering", EmployeeStatus.ACTIVE);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().employeeCode()).isEqualTo("EMP-00007");
    }
}
