package com.dolbaia.ledger.imports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByFileHash(String fileHash);

    @Query("select b from ImportBatch b order by b.createdAt desc")
    List<ImportBatch> findLatest(Limit limit);
}
