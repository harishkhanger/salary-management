package com.acme.salary.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Centralized append-only audit ledger. Salary events are thin references
 * (ref_table + ref_id) to the row owning the amounts — no duplication.
 * run_id is the collapse key for bulk-generated rows in the global feed.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(name = "changed_fields", columnDefinition = "json")
    private String changedFields;

    @Column(name = "ref_table", length = 30)
    private String refTable;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
