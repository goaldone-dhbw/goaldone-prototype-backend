package de.goaldone.backend.repository;

import de.goaldone.backend.entity.Break;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BreakRepository extends JpaRepository<Break, UUID> {
    List<Break> findByUserId(UUID userId);
}
