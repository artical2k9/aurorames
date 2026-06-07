package com.mes.platform.uom.api;

import com.mes.common.security.annotation.RequiresPrivilege;
import com.mes.platform.uom.api.dto.CreateUomRequest;
import com.mes.platform.uom.api.dto.PatchUomRequest;
import com.mes.platform.uom.api.dto.UnitOfMeasureDto;
import com.mes.platform.uom.service.UnitOfMeasureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/uom")
public class UnitOfMeasureController {

    private final UnitOfMeasureService service;

    public UnitOfMeasureController(UnitOfMeasureService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPrivilege("settings:uom:read")
    public List<UnitOfMeasureDto> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive
    ) {
        return includeInactive ? service.listAll() : service.listActive();
    }

    @PostMapping
    @RequiresPrivilege("settings:uom:write")
    public ResponseEntity<UnitOfMeasureDto> create(@Valid @RequestBody CreateUomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{code}")
    @RequiresPrivilege("settings:uom:write")
    public UnitOfMeasureDto patch(
            @PathVariable String code,
            @Valid @RequestBody PatchUomRequest request
    ) {
        return service.patch(code, request);
    }
}
