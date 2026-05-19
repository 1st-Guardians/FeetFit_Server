package com.feetfit.server.web.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "HealthArticle [은서]", description = "건강 아티클 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/articles")
public class HealthArticleController {
}
