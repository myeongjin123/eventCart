package com.eventcart.product.service;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.product.domain.Product;
import com.eventcart.product.dto.ProductDto;
import com.eventcart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductDto.Response create(ProductDto.CreateRequest request) {
        Product product = productRepository.save(new Product(request.name(), request.price()));
        return ProductDto.Response.from(product);
    }

    public ProductDto.Response find(Long productId) {
        return ProductDto.Response.from(getProduct(productId));
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
