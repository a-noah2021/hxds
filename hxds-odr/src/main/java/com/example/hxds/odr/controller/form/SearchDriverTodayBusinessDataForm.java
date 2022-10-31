package com.example.hxds.odr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "SearchDriverTodayBusinessDataForm")
public class SearchDriverTodayBusinessDataForm {

    @NotNull(message = "driverId cannot be null")
    @Min(value = 1, message = "driverId cannot less than 1")
    @Schema(description = "driverId")
    private Long driverId;

}
