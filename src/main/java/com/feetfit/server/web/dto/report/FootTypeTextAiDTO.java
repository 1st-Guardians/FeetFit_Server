package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.MeasurementStatus;

public final class FootTypeTextAiDTO {

    private FootTypeTextAiDTO() {
    }

    public record Request(
            Long measurementSessionId,
            MeasurementStatus measurementStatus,
            String factsHash,
            Analysis analysis
    ) {
    }

    public record Analysis(
            Float measuredLeftFootSizeMm,
            Float measuredRightFootSizeMm,
            Float leftFootWidthMm,
            Float rightFootWidthMm,
            Float leftPressurePercent,
            Float rightPressurePercent,
            String plantarFootprintAnalysisText
    ) {
    }

    public record Response(
            Long measurementSessionId,
            String factsHash,
            String typeText,
            String evidenceId,
            String source
    ) {
    }
}
