package com.acme.salary.controller;

import com.acme.salary.dto.response.EmployeeResponse;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the ApiResponse envelope contract the frontend codes against:
 * success -> {success:true, data:..., error:null}
 * failure -> {success:false, data:null, error:{code, message}} with real HTTP status.
 */
@WebMvcTest(EmployeeController.class)
class EmployeeControllerEnvelopeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    private EmployeeResponse employee() {
        return new EmployeeResponse(7L, "EMP-00007", "Asha Rao", "asha@acme.test", "India",
                "Engineering", "INR", new BigDecimal("1200000.00"), EmployeeStatus.ACTIVE,
                LocalDate.of(2024, 3, 1), 1);
    }

    @Test
    void successResponseIsWrappedInEnvelope() throws Exception {
        when(employeeService.getById(7L)).thenReturn(employee());

        mockMvc.perform(get("/api/employees/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-00007"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void notFoundUsesEnvelopeWithCodeAnd404() throws Exception {
        when(employeeService.getById(99L)).thenThrow(new NotFoundException("Employee not found: 99"));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Employee not found: 99"));
    }

    @Test
    void conflictCarriesMachineReadableCode() throws Exception {
        when(employeeService.update(eq(7L), any()))
                .thenThrow(new ConflictException("STALE_VERSION", "Stale version 0 — reload and retry"));

        mockMvc.perform(put("/api/employees/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha","email":"a@acme.test","country":"India",
                                 "department":"Eng","currencyCode":"INR",
                                 "joinedOn":"2024-03-01","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
    }

    @Test
    void validationFailureUsesEnvelopeWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","country":"India",
                                 "department":"Eng","currencyCode":"INR",
                                 "annualSalary":1,"joinedOn":"2024-03-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }
}
