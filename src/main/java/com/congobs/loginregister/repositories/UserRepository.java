package com.congobs.loginregister.repositories;

import com.congobs.loginregister.models.ApplicationUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<ApplicationUser, Integer> {

    Optional<ApplicationUser> findByEmail(String email);
    List<ApplicationUser> findAllByFirstnameIgnoreCaseOrLastnameIgnoreCase(String firstname, String lastname);
}
