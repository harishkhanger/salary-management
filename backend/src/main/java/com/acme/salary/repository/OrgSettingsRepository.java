package com.acme.salary.repository;

import com.acme.salary.entities.OrgSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgSettingsRepository extends JpaRepository<OrgSettings, Long> {
}
