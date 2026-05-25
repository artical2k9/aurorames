package com.mes.platform.api;

import com.mes.platform.api.dto.SystemConfigDto;
import com.mes.platform.service.SystemConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/config")
public class InternalConfigController {

    private final SystemConfigService configService;

    public InternalConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/{key}")
    public SystemConfigDto getByKey(@PathVariable String key, @RequestParam UUID orgId) {
        return configService.findByKey(orgId, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Config key not found: " + key));
    }
}
