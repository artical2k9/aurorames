package com.mes.common.security.privilege;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaffeinePrivilegeCacheTest {

    @Mock
    private PrivilegeRegistryClient registryClient;

    private CaffeinePrivilegeCache cache;

    @BeforeEach
    void setUp() {
        cache = new CaffeinePrivilegeCache(registryClient, 60L);
    }

    @Test
    void getPrivilegesForRole_cacheMiss_delegatesToRegistry() {
        when(registryClient.fetchManifest()).thenReturn(
                new PrivilegeManifest(Map.of("ROLE_OPERATOR", Set.of("part.read"))));

        assertThat(cache.getPrivilegesForRole("ROLE_OPERATOR")).containsExactly("part.read");
        verify(registryClient, times(1)).fetchManifest();
    }

    @Test
    void getPrivilegesForRole_cacheHit_doesNotCallRegistry() {
        when(registryClient.fetchManifest()).thenReturn(
                new PrivilegeManifest(Map.of("ROLE_OPERATOR", Set.of("part.read"))));

        cache.getPrivilegesForRole("ROLE_OPERATOR");
        cache.getPrivilegesForRole("ROLE_OPERATOR");

        verify(registryClient, times(1)).fetchManifest();
    }

    @Test
    void onPrivilegeChange_evictsRole_nextCallFetchesFresh() {
        when(registryClient.fetchManifest())
                .thenReturn(new PrivilegeManifest(Map.of("ROLE_OPERATOR", Set.of("part.read"))))
                .thenReturn(new PrivilegeManifest(Map.of("ROLE_OPERATOR", Set.of("part.read", "workorder.execute"))));

        assertThat(cache.getPrivilegesForRole("ROLE_OPERATOR")).containsExactly("part.read");

        cache.onPrivilegeChange("ROLE_OPERATOR");

        assertThat(cache.getPrivilegesForRole("ROLE_OPERATOR"))
                .containsExactlyInAnyOrder("part.read", "workorder.execute");
        verify(registryClient, times(2)).fetchManifest();
    }

    @Test
    void getPrivilegesForRole_unknownRole_returnsEmptySet() {
        when(registryClient.fetchManifest()).thenReturn(
                new PrivilegeManifest(Map.of("ROLE_OPERATOR", Set.of("part.read"))));

        assertThat(cache.getPrivilegesForRole("ROLE_UNKNOWN")).isEmpty();
    }
}
