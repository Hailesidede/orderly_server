package com.marketplace.backend.services;

import com.marketplace.backend.dtos.MerchantProductResponse;
import com.marketplace.backend.dtos.ProductCreateRequest;
import com.marketplace.backend.dtos.ProductSummaryResponse;
import com.marketplace.backend.entities.Product;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.ProductRepository;
import com.marketplace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private final S3StorageService s3StorageService;



    @Transactional
    public UUID createProductWithImage(ProductCreateRequest request, MultipartFile image, UUID merchantId) {

        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found."));

        // 1. Upload to S3 First. If this fails, the method throws an exception and the DB never saves.
        String imageUrl = s3StorageService.storeFile(image); // Remove the productId parameter from your S3 service

        // 2. Build and save the product
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .shadowDeliveryFee(request.shadowDeliveryFee())
                .stockQuantity(request.stockQuantity())
                .imageUrl(imageUrl) // The S3 URL
                .merchant(merchant)
                .build();

        return productRepository.save(product).getId();
    }


    @Transactional
    public UUID createProduct(ProductCreateRequest request, UUID merchantId) {

        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found."));
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .shadowDeliveryFee(request.shadowDeliveryFee())
                .stockQuantity(request.stockQuantity())
                 .merchant(merchant) //<-- If you decide to link products to specific merchants later
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("New product created with ID: {} and initial stock: {}", savedProduct.getId(), savedProduct.getStockQuantity());

        return savedProduct.getId();
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAvailableProducts(String categoryId, Pageable pageable) {
        Page<Product> productPage;

        // Enterprise logic: Filter at the DB level, only fetch items with stock > 0
        if (categoryId == null || categoryId.equalsIgnoreCase("all")) {
            productPage = productRepository.findByStockQuantityGreaterThan(0, pageable);
        } else {
            productPage = productRepository.findByCategoryIdAndStockQuantityGreaterThan(categoryId, 0, pageable);
        }

        return productPage.map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<MerchantProductResponse> getMyProducts(UUID merchantId) {

        // Fetch from repository
        List<Product> products = productRepository.findByMerchantId(merchantId);

        // Map Entities to the Merchant DTO
        return products.stream()
                .map(product -> new MerchantProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getPrice(),
                        product.getStockQuantity(),
                        product.getImageUrl()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAvailableProductsByMerchant(String merchantId, Pageable pageable) {
        Page<Product> productPage;
        if (merchantId == null || merchantId.equalsIgnoreCase("all")) {
            throw new IllegalArgumentException("Please select a merchant to view their products.");
        } else {
            UUID mId = UUID.fromString(merchantId);
            productPage = productRepository.findByMerchantIdAndStockQuantityGreaterThan(mId, 0, pageable);
        }
        return productPage.map(this::mapToSummaryResponse);
    }

    private ProductSummaryResponse mapToSummaryResponse(Product product) {
        User merchant = product.getMerchant();
        String storeName = "Unknown Store";
        if (merchant != null) {
            if (merchant.getMerchantProfile() != null && merchant.getMerchantProfile().getStoreName() != null) {
                storeName = merchant.getMerchantProfile().getStoreName();
            } else {
                storeName = merchant.getFirstName() + "'s Store";
            }
        }
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryId(),
                product.getPrice(),
                product.getImageUrl(),
                storeName
        );
    }
}
