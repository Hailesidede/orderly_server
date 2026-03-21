package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllByIdWithPessimisticWriteLock(@Param("ids") List<UUID> ids);

//    Page<Product> findByCategoryIdAndStockQuantityGreaterThan(String categoryId, Integer stockQuantity, Pageable pageable);

    Page<Product> findByStockQuantityGreaterThan(Integer stockQuantity, Pageable pageable);

        List<Product> findByMerchantId(UUID merchantId);

//    Page<Product> findByMerchantIdAndStockQuantityGreaterThan(UUID merchantId, int stockQuantity, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant m LEFT JOIN FETCH m.merchantProfile WHERE p.stockQuantity > :quantity")
    Page<Product> findByStockQuantityGreaterThan(int quantity, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant m LEFT JOIN FETCH m.merchantProfile WHERE p.merchant.id = :merchantId AND p.stockQuantity > :quantity")
    Page<Product> findByMerchantIdAndStockQuantityGreaterThan(UUID merchantId, int quantity, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.merchant m LEFT JOIN FETCH m.merchantProfile WHERE p.categoryId = :categoryId AND p.stockQuantity > :quantity")
    Page<Product> findByCategoryIdAndStockQuantityGreaterThan(String categoryId, int quantity, Pageable pageable);

}
