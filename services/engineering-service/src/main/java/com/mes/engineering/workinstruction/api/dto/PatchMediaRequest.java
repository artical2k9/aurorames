package com.mes.engineering.workinstruction.api.dto;

import jakarta.validation.constraints.Size;

/** Edit a media attachment's caption and/or display order. */
public class PatchMediaRequest {

    @Size(max = 500)
    private String caption;

    private Integer displayOrder;

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
