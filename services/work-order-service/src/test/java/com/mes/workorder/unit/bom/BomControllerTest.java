package com.mes.workorder.unit.bom;

import com.mes.workorder.bom.api.BomController;
import com.mes.workorder.bom.api.dto.BomLineDto;
import com.mes.workorder.bom.api.dto.BomMapper;
import com.mes.workorder.bom.api.dto.UpdateBomLineRequest;
import com.mes.workorder.bom.domain.BomLine;
import com.mes.workorder.bom.service.BomExplosionService;
import com.mes.workorder.bom.service.BomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomControllerTest {

    @Mock BomService bomService;
    @Mock BomExplosionService explosionService;

    @InjectMocks
    BomController controller;

    UUID orgId = UUID.randomUUID();
    UUID bomId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();

    @Test
    void updateLineDelegatesToServiceAndReturnsMappedDto() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("org_id")).thenReturn(orgId.toString());

        BomLine line = new BomLine();
        line.setBomId(bomId);
        line.setFindNumber("010");
        line.setQuantity(new BigDecimal("3.0"));

        when(bomService.updateLine(eq(orgId), eq(bomId), eq(lineId), any())).thenReturn(line);
        when(bomService.enrichLine(line)).thenReturn(BomMapper.toLineDto(line));

        UpdateBomLineRequest req = new UpdateBomLineRequest();
        req.setQuantity(new BigDecimal("3.0"));

        BomLineDto result = controller.updateLine(jwt, bomId, lineId, req);

        assertThat(result.getBomId()).isEqualTo(bomId);
        assertThat(result.getFindNumber()).isEqualTo("010");
        verify(bomService).updateLine(eq(orgId), eq(bomId), eq(lineId), any());
    }
}
