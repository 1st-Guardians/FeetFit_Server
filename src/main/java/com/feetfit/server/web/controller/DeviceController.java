package com.feetfit.server.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Device", description = "기기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {
}
