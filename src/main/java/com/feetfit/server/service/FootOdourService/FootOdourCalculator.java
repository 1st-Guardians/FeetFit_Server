package com.feetfit.server.service.FootOdourService;

public class FootOdourCalculator {

    private static final float MAX_DISPLAY_PPM = 5.0f;

    public static FootOdourResult calculate(Integer tvocPpb, Integer baselineTvocPpb) {
        int rawTvoc = (tvocPpb != null && tvocPpb > 0) ? tvocPpb : 0;
        int baseline = (baselineTvocPpb != null && baselineTvocPpb > 0) ? baselineTvocPpb : 0;
        int correctedPpb = Math.max(rawTvoc - baseline, 0);
        float rawPpm = correctedPpb / 1000.0f;
        float displayPpm = Math.min(rawPpm, MAX_DISPLAY_PPM);
        String comment = generateComment(displayPpm);
        return new FootOdourResult(displayPpm, rawPpm, comment);
    }

    static String generateComment(float displayPpm) {
        String x = String.format("%.2f", displayPpm);
        if (displayPpm <= 0.30f) {
            return "발냄새 위험도는 " + x + "ppm으로 낮은 편이에요. 현재는 냄새 걱정이 크지 않은 상태예요.";
        } else if (displayPpm <= 1.00f) {
            return "발냄새 위험도는 " + x + "ppm으로 보통 수준이에요. 양말 교체나 발 건조를 해주면 좋아요.";
        } else if (displayPpm <= 3.00f) {
            return "발냄새 위험도는 " + x + "ppm으로 높은 편이에요. 발을 씻고 완전히 건조한 뒤, 신발도 충분히 환기해 주세요.";
        } else {
            return "발냄새 위험도는 " + x + "ppm으로 매우 높은 편이에요. 발과 신발의 습기 관리가 필요하며, 냄새가 지속되면 발 상태를 함께 확인해 주세요.";
        }
    }

    public record FootOdourResult(float displayPpm, float rawPpm, String comment) {}
}
