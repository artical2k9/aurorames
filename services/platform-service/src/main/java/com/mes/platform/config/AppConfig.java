package com.mes.platform.config;

import com.mes.platform.preferences.api.dto.ColumnPreferenceEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class AppConfig {

    @Bean
    public Map<String, List<ColumnPreferenceEntry>> defaultColumnRegistry() {
        return Map.of(
                "ITEM_MASTER", List.of(
                        new ColumnPreferenceEntry("partNumber", true, 0),
                        new ColumnPreferenceEntry("revision", true, 1),
                        new ColumnPreferenceEntry("description", true, 2),
                        new ColumnPreferenceEntry("classification", true, 3),
                        new ColumnPreferenceEntry("makeBuyCode", true, 4),
                        new ColumnPreferenceEntry("unitOfMeasure", true, 5),
                        new ColumnPreferenceEntry("status", true, 6),
                        new ColumnPreferenceEntry("counterfeitRiskLevel", false, 7),
                        new ColumnPreferenceEntry("cageCode", false, 8)
                )
        );
    }
}
