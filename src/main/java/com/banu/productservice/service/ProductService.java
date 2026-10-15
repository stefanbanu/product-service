package com.banu.productservice.service;

import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Where the entity becomes a response, so the controller never sees a Product and the
 * repository never sees a DTO.
 *
 * <p>Constructor injection without {@code @Autowired}: a single constructor is enough for
 * Spring to use it, and it keeps the field final.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<ProductResponse> findAll() {
        return repository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
