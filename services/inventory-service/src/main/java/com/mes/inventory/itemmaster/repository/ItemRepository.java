package com.mes.inventory.itemmaster.repository;

import com.mes.inventory.itemmaster.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    Optional<Item> findByOrgIdAndId(UUID orgId, UUID id);

    boolean existsByOrgIdAndPartNumber(UUID orgId, String partNumber);
}
