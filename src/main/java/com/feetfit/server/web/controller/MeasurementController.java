package com.feetfit.server.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Measurement [은서]", description = "측정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/measurements")
public class MeasurementController {
}
