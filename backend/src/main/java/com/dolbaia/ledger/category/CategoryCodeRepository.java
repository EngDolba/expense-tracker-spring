package com.dolbaia.ledger.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryCodeRepository extends JpaRepository<CategoryCode, UUID> {

    Optional<CategoryCode> findByCode(String code);

    @Query("select c from CategoryCode c join fetch c.category order by c.code")
    List<CategoryCode> findAllWithCategory();
}
