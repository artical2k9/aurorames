package com.mikemes.common.security.privilege;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeinePrivilegeCache implements PrivilegeCache {

    private final Cache<String, Set<String>> cache;
    private final PrivilegeRegistryClient registryClient;

    public CaffeinePrivilegeCache(
            PrivilegeRegistryClient registryClient,
            @Value("${mikemes.security.privilege-cache-ttl-seconds:60}") long ttlSeconds) {
        this.registryClient = registryClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Set<String> getPrivilegesForRole(String role) {
        return cache.get(role, k -> registryClient.fetchManifest().getPrivilegesForRole(k));
    }

    @KafkaListener(
            topics = "${mikemes.security.privilege-changes-topic:iam.privilege-changes}",
            groupId = "${spring.application.name:lib-common-security}-privilege-cache")
    public void onPrivilegeChange(String role) {
        cache.invalidate(role);
    }
}
