package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.MerchantProductResponse;
import com.marketplace.backend.dtos.ProductCreateRequest;
import com.marketplace.backend.dtos.ProductSummaryResponse;
import com.marketplace.backend.entities.Product;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.ProductRepository;
import com.marketplace.backend.services.FileStorageService;
import com.marketplace.backend.services.ProductService;
import com.marketplace.backend.services.S3StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;

    private final S3StorageService s3StorageService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MERCHANT')")
    public ResponseEntity<Map<String, UUID>> createProduct(
            @RequestPart("product") @Valid ProductCreateRequest request,
            @RequestPart(value = "image", required = true) MultipartFile image,
            @AuthenticationPrincipal User authenticatedMerchant) {

        UUID productId = productService.createProductWithImage(request, image, authenticatedMerchant.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("productId", productId));
    }

    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MERCHANT')") // Don't forget security!
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @PathVariable UUID productId,
            @RequestParam("image") MultipartFile imageFile) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // This now returns a permanent absolute URL (e.g., https://bucket.s3.com/products/...)
        String imageUrl = s3StorageService.storeFile(imageFile);

        product.setImageUrl(imageUrl);
        productRepository.save(product);

        return ResponseEntity.ok(Map.of(
                "message", "Image uploaded successfully",
                "imageUrl", imageUrl
        ));
    }



    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false, defaultValue = "all") String categoryId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ProductSummaryResponse> products = productService.getAvailableProductsByMerchant(categoryId, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('MERCHANT')")
    public ResponseEntity<List<MerchantProductResponse>> getMyProducts(@AuthenticationPrincipal User merchant) {

        List<MerchantProductResponse> myProducts = productService.getMyProducts(merchant.getId());

        return ResponseEntity.ok(myProducts);
    }


    //    @PostMapping
//    public ResponseEntity<Map<String, UUID>> createProduct(@Valid @RequestBody ProductCreateRequest request,
//                                                           @AuthenticationPrincipal User authenticatedMerchant) {
//        UUID productId = productService.createProduct(request,authenticatedMerchant.getId());
//        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("productId", productId));
//    }

    //    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<Map<String, String>> uploadProductImage(
//            @PathVariable UUID productId,
//            @RequestParam("image") MultipartFile imageFile) { // "image" is the key Postman will use
//
//        // 1. Verify product exists
//        Product product = productRepository.findById(productId)
//                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
//
//        // 2. Save the physical file and get the URL
//        String imageUrl = fileStorageService.storeFile(imageFile, productId);
//
//        // 3. Update the database record
//        product.setImageUrl(imageUrl);
//        productRepository.save(product);
//
//        return ResponseEntity.ok(Map.of(
//                "message", "Image uploaded successfully",
//                "imageUrl", imageUrl
//        ));
//    }
}
