package com.mikemes.iam.integration.privilege;

import com.github.benmanes.caffeine.cache.Cache;
import com.mikemes.common.security.privilege.CaffeinePrivilegeCache;
import com.mikemes.common.security.privilege.PrivilegeRegistryClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
class PrivilegeCacheInvalidationIT {

    @TestConfiguration
    static class CacheConfig {
        @Bean
        @Primary
        CaffeinePrivilegeCache caffeinePrivilegeCache(
                PrivilegeRegistryClient registryClient,
                @Value("${mikemes.security.privilege-cache-ttl-seconds:60}") long ttlSeconds) {
            return new CaffeinePrivilegeCache(registryClient, ttlSeconds);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mikemes")
            .withUsername("iam_user")
            .withPassword("secret");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/jwks");
        registry.add("keycloak.admin.server-url", () -> "http://localhost:1");
        registry.add("keycloak.admin.realm", () -> "test");
        registry.add("keycloak.admin.username", () -> "admin");
        registry.add("keycloak.admin.password", () -> "admin");
    }

    @Autowired
    CaffeinePrivilegeCache privilegeCache;

    @Autowired
    KafkaTemplate<Object, Object> kafkaTemplate;

    @SuppressWarnings("unchecked")
    private Cache<String, Set<String>> caffeineCache() {
        return (Cache<String, Set<String>>) ReflectionTestUtils.getField(privilegeCache, "cache");
    }

    @BeforeEach
    void clearCache() {
        caffeineCache().invalidateAll();
    }

    @Test
    void kafkaPrivilegeChange_invalidatesCacheEntry() {
        Cache<String, Set<String>> cache = caffeineCache();
        cache.put("ADMIN", Set.of("iam:roles:manage", "iam:users:view"));
        assertThat(cache.getIfPresent("ADMIN")).isNotNull();

        kafkaTemplate.send("iam.privilege-changes", "ADMIN", "ADMIN");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(cache.getIfPresent("ADMIN")).isNull());
    }

    @Test
    void kafkaPrivilegeChange_onlyInvalidatesTargetedRole() {
        Cache<String, Set<String>> cache = caffeineCache();
        cache.put("ADMIN", Set.of("iam:roles:manage"));
        cache.put("VIEWER", Set.of("iam:users:view"));

        kafkaTemplate.send("iam.privilege-changes", "ADMIN", "ADMIN");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(cache.getIfPresent("ADMIN")).isNull());

        assertThat(cache.getIfPresent("VIEWER"))
                .isNotNull()
                .containsExactly("iam:users:view");
    }

    @Test
    void kafkaPrivilegeChange_multipleMessages_invalidatesAllTargetedRoles() {
        Cache<String, Set<String>> cache = caffeineCache();
        cache.put("ADMIN", Set.of("iam:roles:manage"));
        cache.put("OPERATOR", Set.of("iam:users:view"));
        cache.put("VIEWER", Set.of("iam:privileges:view"));

        kafkaTemplate.send("iam.privilege-changes", "ADMIN", "ADMIN");
        kafkaTemplate.send("iam.privilege-changes", "OPERATOR", "OPERATOR");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(cache.getIfPresent("ADMIN")).isNull();
                    assertThat(cache.getIfPresent("OPERATOR")).isNull();
                });

        assertThat(cache.getIfPresent("VIEWER")).isNotNull();
    }
}
