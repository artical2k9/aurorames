package com.mes.routing.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.routing.route.service.RouteSupersedeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupersededEventListenerTest {

    @Mock RouteSupersedeService supersedeService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks SupersededEventListener listener;

    @Test
    void onBomRevisionSuperseded_validMessage_marksRoutes() {
        UUID rev = UUID.randomUUID();
        listener.onBomRevisionSuperseded("{\"revisionId\":\"" + rev + "\"}");
        verify(supersedeService).markBomRevisionSuperseded(rev);
    }

    @Test
    void onInspectionPlanRevisionSuperseded_validMessage_marksRoutes() {
        UUID rev = UUID.randomUUID();
        listener.onInspectionPlanRevisionSuperseded("{\"revisionId\":\"" + rev + "\"}");
        verify(supersedeService).markInspectionPlanRevisionSuperseded(rev);
    }

    @Test
    void malformedJson_isSwallowed() {
        listener.onBomRevisionSuperseded("{not json");
        verify(supersedeService, never()).markBomRevisionSuperseded(any());
    }

    @Test
    void missingRevisionId_isSkipped() {
        listener.onBomRevisionSuperseded("{\"other\":\"x\"}");
        verify(supersedeService, never()).markBomRevisionSuperseded(any());
    }

    @Test
    void invalidUuid_isSwallowed() {
        listener.onInspectionPlanRevisionSuperseded("{\"revisionId\":\"not-a-uuid\"}");
        verify(supersedeService, never()).markInspectionPlanRevisionSuperseded(any());
    }
}
