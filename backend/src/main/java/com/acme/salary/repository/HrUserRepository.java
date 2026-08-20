package com.acme.salary.repository;

import com.acme.salary.entities.HrUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HrUserRepository extends JpaRepository<HrUser, Long> {

    Optional<HrUser> findByUsername(String username);
}
