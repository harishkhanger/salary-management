package com.acme.salary.audit;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.response.AuditFeedItem;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.entities.AuditLog;
import com.acme.salary.repository.AuditLogRepository;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.PayrollRunRepository;
import com.acme.salary.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB contract: the feed's (created_at DESC, id DESC) ordering walks the whole
 * table page by page in strict order with no duplicates and no gaps — the id
 * tiebreaker is what keeps page boundaries stable inside equal-timestamp
 * groups (a bare created_at sort would let rows shuffle between pages).
 */
@DataJpaTest
class AuditFeedDbContractTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private BulkRaiseRunRepository bulkRaiseRunRepository;

    @Autowired
    private com.acme.salary.repository.EmployeeRepository employeeRepository;

    private AuditService auditService;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, payrollRunRepository,
                bulkRaiseRunRepository, employeeRepository, new PaginationProperties(100), new ObjectMapper());
        // 30 rows across 10 distinct timestamps -> 3 rows share each timestamp
        for (int i = 0; i < 30; i++) {
            auditLogRepository.save(AuditLog.builder()
                    .entityType("EMPLOYEE").entityId((long) i)
                    .action("PROFILE_UPDATED").actor("hr")
                    .createdAt(BASE.plusMinutes(i / 3))
                    .build());
        }
    }

    private PageResponse<AuditFeedItem> page(int page, int size) {
        return auditService.feed(page, size, null, null, null, null, null, null, null, null);
    }

    @Test
    void walksTheWholeFeedInOrderWithoutDuplicatesOrGaps() {
        Set<Long> seen = new LinkedHashSet<>();
        List<LocalDateTime> timestamps = new ArrayList<>();
        PageResponse<AuditFeedItem> first = page(0, 7);
        assertThat(first.totalElements()).isEqualTo(30);
        assertThat(first.totalPages()).isEqualTo(5);   // 7+7+7+7+2

        for (int p = 0; p < first.totalPages(); p++) {
            page(p, 7).content().forEach(item -> {
                assertThat(seen.add(item.id())).as("duplicate id %s", item.id()).isTrue();
                timestamps.add(item.createdAt());
            });
        }

        assertThat(seen).hasSize(30);          // no gaps: every row surfaced exactly once
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).as("descending order at %s", i)
                    .isBeforeOrEqualTo(timestamps.get(i - 1));
        }
    }

    @Test
    void pageBoundaryInsideEqualTimestampGroupDoesNotSkipRows() {
        // size 2 forces boundaries inside the 3-row same-timestamp groups
        Set<Long> seen = new LinkedHashSet<>();
        for (int p = 0; p < 15; p++) {
            page(p, 2).content().forEach(item -> assertThat(seen.add(item.id())).isTrue());
        }
        assertThat(seen).hasSize(30);
    }

    @Test
    void filtersNarrowTheFeedAndItsTotal() {
        // decoys outside the filter: different action, actor, and day
        auditLogRepository.save(AuditLog.builder()
                .entityType("EMPLOYEE").entityId(99L)
                .action("STATUS_CHANGED").actor("hr").createdAt(BASE).build());
        auditLogRepository.save(AuditLog.builder()
                .entityType("EMPLOYEE").entityId(98L)
                .action("PROFILE_UPDATED").actor("system").createdAt(BASE).build());
        auditLogRepository.save(AuditLog.builder()
                .entityType("EMPLOYEE").entityId(97L)
                .action("PROFILE_UPDATED").actor("hr")
                .createdAt(BASE.minusDays(5)).build());

        PageResponse<AuditFeedItem> filtered = auditService.feed(0, 50, null, null, null, null,
                "PROFILE_UPDATED", "hr", BASE.toLocalDate(), BASE.toLocalDate());

        assertThat(filtered.totalElements()).isEqualTo(30);   // the 3 decoys are excluded
        assertThat(filtered.content()).allSatisfy(item -> {
            assertThat(item.action()).isEqualTo("PROFILE_UPDATED");
            assertThat(item.actor()).isEqualTo("hr");
        });
    }
}
