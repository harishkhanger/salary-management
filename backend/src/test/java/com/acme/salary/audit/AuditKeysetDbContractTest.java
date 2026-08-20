package com.acme.salary.audit;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.response.AuditFeedResponse;
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
 * DB contract: keyset pagination over (created_at DESC, id DESC) walks the
 * whole feed in strict order with no duplicates and no gaps — including
 * across rows sharing the same timestamp (the id tiebreaker's job).
 */
@DataJpaTest
class AuditKeysetDbContractTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private BulkRaiseRunRepository bulkRaiseRunRepository;

    private AuditService auditService;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, payrollRunRepository,
                bulkRaiseRunRepository, new PaginationProperties(100), new ObjectMapper());
        // 30 rows across 10 distinct timestamps -> 3 rows share each timestamp
        for (int i = 0; i < 30; i++) {
            auditLogRepository.save(AuditLog.builder()
                    .entityType("EMPLOYEE").entityId((long) i)
                    .action("PROFILE_UPDATED").actor("hr")
                    .createdAt(BASE.plusMinutes(i / 3))
                    .build());
        }
    }

    @Test
    void walksTheWholeFeedInOrderWithoutDuplicatesOrGaps() {
        Set<Long> seen = new LinkedHashSet<>();
        List<LocalDateTime> timestamps = new ArrayList<>();
        String cursor = null;
        int pages = 0;

        do {
            AuditFeedResponse page = auditService.feed(cursor, 7, null, null, null, null);
            page.items().forEach(item -> {
                assertThat(seen.add(item.id())).as("duplicate id %s", item.id()).isTrue();
                timestamps.add(item.createdAt());
            });
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(seen).hasSize(30);          // no gaps: every row surfaced exactly once
        assertThat(pages).isEqualTo(5);        // 7+7+7+7+2
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).as("descending order at %s", i)
                    .isBeforeOrEqualTo(timestamps.get(i - 1));
        }
    }

    @Test
    void pageBoundaryInsideEqualTimestampGroupDoesNotSkipRows() {
        // limit 2 forces boundaries inside the 3-row same-timestamp groups
        Set<Long> seen = new LinkedHashSet<>();
        String cursor = null;
        do {
            AuditFeedResponse page = auditService.feed(cursor, 2, null, null, null, null);
            page.items().forEach(item -> assertThat(seen.add(item.id())).isTrue());
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(seen).hasSize(30);
    }
}
