package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Search Driver Today Business Data Form")
public class SearchDriverTodayBusinessDataForm {
    @Schema(description = "DriverId")
    private Long driverId;
}
