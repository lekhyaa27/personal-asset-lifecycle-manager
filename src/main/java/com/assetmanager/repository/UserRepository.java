package com.assetmanager.repository;

import com.assetmanager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Spring writes the SQL for these method names automatically (SELECT ... WHERE email = ?). */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
