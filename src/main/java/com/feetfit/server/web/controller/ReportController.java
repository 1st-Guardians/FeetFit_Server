package com.feetfit.server.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {
}
