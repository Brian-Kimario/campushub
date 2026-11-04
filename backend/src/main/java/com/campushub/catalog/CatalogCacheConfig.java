package com.campushub.catalog;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables catalog caching (FR-CAT-04, ADR-010). Only public catalog reads are cached. Each admin write evicts
 * the cache it touches, so a stale value does not outlive the write.
 */
@Configuration
@EnableCaching
class CatalogCacheConfig {

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CatalogService.DEPARTMENTS, CatalogService.TERMS,
                CatalogService.COURSES);
    }
}
