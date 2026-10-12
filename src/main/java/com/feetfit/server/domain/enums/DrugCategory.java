package com.feetfit.server.domain.enums;

import java.util.Arrays;

public enum DrugCategory {
    ATHLETES_FOOT("athletes-foot", "무좀약", "항진균제", "증상과 사용 부위를 확인하고 약국에서 상담하세요.", false, "mint", "Footprints"),
    BUNION_BAND("bunion-band", "무지외반증 교정밴드", "발 교정용품", "착용 방식과 사이즈를 비교해 온라인으로 구매할 수 있어요.", true, "lavender", "ShieldCheck"),
    WOUND_CARE("wound-care", "상처·발 관리", "약국·온라인", "상처 치료 연고와 보호밴드·드레싱, 발가락 보호용품을 함께 확인하세요.", false, "coral", "Bandage");

    private final String id;
    private final String label;
    private final String eyebrow;
    private final String description;
    private final boolean online;
    private final String accent;
    private final String iconName;

    DrugCategory(String id, String label, String eyebrow, String description,
                 boolean online, String accent, String iconName) {
        this.id = id;
        this.label = label;
        this.eyebrow = eyebrow;
        this.description = description;
        this.online = online;
        this.accent = accent;
        this.iconName = iconName;
    }

    public static DrugCategory fromIdOrDefault(String id) {
        if (id == null || id.isBlank()) {
            return ATHLETES_FOOT;
        }
        String normalizedId = id.trim().toLowerCase();
        if (normalizedId.equals("wound-ointment")
                || normalizedId.equals("wound-dressing")
                || normalizedId.equals("foot-care")) {
            return WOUND_CARE;
        }
        return Arrays.stream(values())
                .filter(category -> category.id.equalsIgnoreCase(normalizedId))
                .findFirst()
                .orElse(ATHLETES_FOOT);
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getEyebrow() {
        return eyebrow;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOnline() {
        return online;
    }

    public String getAccent() {
        return accent;
    }

    public String getIconName() {
        return iconName;
    }
}
