package com.mes.workorder.unit;

import com.mes.common.security.privilege.PrivilegeRegistration;
import com.mes.common.security.privilege.PrivilegeRegistryClient;
import com.mes.common.security.privilege.PrivilegeRegistryException;
import com.mes.workorder.WorkOrderServiceApplication;
import com.mes.workorder.health.PrivilegeRegistrationHealthIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceApplicationTest {

    @Mock PrivilegeRegistryClient privilegeRegistryClient;
    @Mock PrivilegeRegistrationHealthIndicator healthIndicator;

    @InjectMocks
    WorkOrderServiceApplication application;

    @Test
    void registersAllFiveItemMasterPrivilegesOnReadyEvent() {
        application.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(privilegeRegistryClient).register(eq("item-master"), argThat(list ->
                list.size() == 5 &&
                list.stream().map(PrivilegeRegistration::privilegeKey).toList().containsAll(List.of(
                        "item-master:records:view",
                        "item-master:records:manage",
                        "item-master:bom:manage",
                        "item-master:eco:manage",
                        "item-master:udf:manage"))));
    }

    @Test
    void marksHealthIndicatorAfterSuccessfulRegistration() {
        application.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(healthIndicator).markRegistered();
    }

    @Test
    void continuesStartupIfRegistrationFails() {
        doThrow(new PrivilegeRegistryException("IAM unavailable", null))
                .when(privilegeRegistryClient).register(any(), any());

        assertThatCode(() -> application.onApplicationEvent(mock(ApplicationReadyEvent.class)))
                .doesNotThrowAnyException();
        verify(healthIndicator, never()).markRegistered();
    }
}
