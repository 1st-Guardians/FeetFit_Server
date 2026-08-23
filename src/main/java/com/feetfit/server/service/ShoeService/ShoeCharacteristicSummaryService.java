package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.BREATHABILITY;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.CUSHION;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.ENERGY_RETURN;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.HEEL_HOLD;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.SHOCK_ABSORPTION;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.TOEBOX_SPACE;
import static com.feetfit.server.domain.enums.ShoeLabCharacteristic.WIDTH_SPACE;

/** Builds a deterministic product-characteristics summary without an LLM. */
@Service
public class ShoeCharacteristicSummaryService {

    private static final List<ShoeLabCharacteristic> COMFORT_GROUP =
            List.of(CUSHION, SHOCK_ABSORPTION, ENERGY_RETURN);
    private static final List<ShoeCharacteristicLevel> LEVEL_SUMMARY_ORDER =
            List.of(
                    ShoeCharacteristicLevel.HIGH,
                    ShoeCharacteristicLevel.MEDIUM,
                    ShoeCharacteristicLevel.LOW);

    /**
     * Summarizes only the non-null characteristic levels supplied by the caller.
     * The result describes the shoe itself; it does not infer user fit, medical
     * effects, or suitability for a particular activity.
     */
    public String summarize(Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels) {
        EnumMap<ShoeLabCharacteristic, ShoeCharacteristicLevel> available =
                validLevels(levels);
        if (available.isEmpty()) {
            return null;
        }

        if (available.size() == 1) {
            Map.Entry<ShoeLabCharacteristic, ShoeCharacteristicLevel> entry =
                    available.entrySet().iterator().next();
            return singleCharacteristicSentence(entry.getKey(), entry.getValue());
        }

        List<String> sentences = new ArrayList<>(3);
        String comfort = comfortSentence(available);
        String space = spaceSentence(available);
        String structureAndAir = structureAndAirSentence(available);

        if (comfort != null) {
            sentences.add(comfort);
        }
        if (space != null) {
            sentences.add(space);
        }
        if (structureAndAir != null) {
            sentences.add(structureAndAir);
        }

        return String.join(" ", sentences);
    }

    private EnumMap<ShoeLabCharacteristic, ShoeCharacteristicLevel> validLevels(
            Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels) {
        EnumMap<ShoeLabCharacteristic, ShoeCharacteristicLevel> available =
                new EnumMap<>(ShoeLabCharacteristic.class);
        if (levels == null) {
            return available;
        }

        levels.forEach((type, level) -> {
            if (type != null && level != null) {
                available.put(type, level);
            }
        });
        return available;
    }

    private String singleCharacteristicSentence(
            ShoeLabCharacteristic type,
            ShoeCharacteristicLevel level) {
        String name = switch (type) {
            case CUSHION -> "쿠션감";
            case SHOCK_ABSORPTION -> "충격 완화";
            case ENERGY_RETURN -> "반발력";
            case WIDTH_SPACE -> "발볼 여유";
            case TOEBOX_SPACE -> "앞코 여유";
            case HEEL_HOLD -> "뒤꿈치 구조의 강성";
            case BREATHABILITY -> "통기성";
        };
        return name + subjectParticle(name) + " " + levelPhrase(level)
                + "인 신발입니다.";
    }

    private String comfortSentence(
            Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels) {
        EnumMap<ShoeCharacteristicLevel, List<String>> namesByLevel =
                new EnumMap<>(ShoeCharacteristicLevel.class);

        for (ShoeLabCharacteristic type : COMFORT_GROUP) {
            ShoeCharacteristicLevel level = levels.get(type);
            if (level != null) {
                namesByLevel.computeIfAbsent(level, ignored -> new ArrayList<>())
                        .add(comfortName(type));
            }
        }

        List<LevelGroup> groups = new ArrayList<>(3);
        for (ShoeCharacteristicLevel level : LEVEL_SUMMARY_ORDER) {
            List<String> names = namesByLevel.get(level);
            if (names != null && !names.isEmpty()) {
                groups.add(new LevelGroup(joinWithAnd(names), level));
            }
        }

        if (groups.isEmpty()) {
            return null;
        }
        if (groups.size() == 1) {
            LevelGroup group = groups.get(0);
            String all = group.names().contains("과") || group.names().contains("와")
                    || group.names().contains(", ") ? " 모두" : "";
            return group.names() + subjectParticle(group.names()) + all + " "
                    + levelPhrase(group.level()) + "인 신발입니다.";
        }

        StringBuilder sentence = new StringBuilder();
        for (int index = 0; index < groups.size(); index++) {
            LevelGroup group = groups.get(index);
            sentence.append(group.names())
                    .append(subjectParticle(group.names()))
                    .append(' ')
                    .append(levelPhrase(group.level()));

            if (index == groups.size() - 1) {
                sentence.append("인 신발입니다.");
            } else if (index == 0) {
                sentence.append("이고 ");
            } else {
                sentence.append("이며 ");
            }
        }
        return sentence.toString();
    }

    private String spaceSentence(Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels) {
        ShoeCharacteristicLevel width = levels.get(WIDTH_SPACE);
        ShoeCharacteristicLevel toebox = levels.get(TOEBOX_SPACE);

        if (width == null && toebox == null) {
            return null;
        }
        if (width == null) {
            return "앞코 공간은 " + spacePhrase(toebox, false) + "입니다.";
        }
        if (toebox == null) {
            return "발볼은 " + spacePhrase(width, true) + "입니다.";
        }
        if (width == toebox) {
            return "발볼과 앞코 공간은 모두 " + sharedSpacePhrase(width) + "입니다.";
        }

        String connector = isOpposite(width, toebox) ? "인 반면 " : "이며 ";
        return "발볼은 " + spacePhrase(width, true) + connector
                + "앞코 공간은 " + spacePhrase(toebox, false) + "입니다.";
    }

    private String structureAndAirSentence(
            Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels) {
        ShoeCharacteristicLevel heel = levels.get(HEEL_HOLD);
        ShoeCharacteristicLevel breathability = levels.get(BREATHABILITY);

        if (heel == null && breathability == null) {
            return null;
        }
        if (heel == null) {
            return "통기성은 " + levelPhrase(breathability) + "입니다.";
        }
        if (breathability == null) {
            return "뒤꿈치 구조는 " + heelStructurePhrase(heel) + "입니다.";
        }
        if (heel == breathability) {
            return "뒤꿈치 구조의 강성과 통기성은 모두 "
                    + levelPhrase(heel) + "입니다.";
        }

        return "뒤꿈치 구조는 " + heelStructurePhrase(heel)
                + "이며 통기성은 " + levelPhrase(breathability) + "입니다.";
    }

    private String comfortName(ShoeLabCharacteristic type) {
        return switch (type) {
            case CUSHION -> "쿠션감";
            case SHOCK_ABSORPTION -> "충격 완화";
            case ENERGY_RETURN -> "반발력";
            default -> throw new IllegalArgumentException("Not a comfort characteristic: " + type);
        };
    }

    private String levelPhrase(ShoeCharacteristicLevel level) {
        return switch (level) {
            case HIGH -> "높은 편";
            case MEDIUM -> "보통 수준";
            case LOW -> "낮은 편";
        };
    }

    private String spacePhrase(ShoeCharacteristicLevel level, boolean width) {
        return switch (level) {
            case HIGH -> width ? "비교적 여유로운 편" : "넓은 편";
            case MEDIUM -> "보통 수준";
            case LOW -> "좁은 편";
        };
    }

    private String sharedSpacePhrase(ShoeCharacteristicLevel level) {
        return switch (level) {
            case HIGH -> "비교적 여유로운 편";
            case MEDIUM -> "보통 수준";
            case LOW -> "좁은 편";
        };
    }

    private String heelStructurePhrase(ShoeCharacteristicLevel level) {
        return switch (level) {
            case HIGH -> "비교적 단단한 편";
            case MEDIUM -> "보통 수준";
            case LOW -> "비교적 유연한 편";
        };
    }

    private boolean isOpposite(
            ShoeCharacteristicLevel first,
            ShoeCharacteristicLevel second) {
        return (first == ShoeCharacteristicLevel.HIGH && second == ShoeCharacteristicLevel.LOW)
                || (first == ShoeCharacteristicLevel.LOW
                && second == ShoeCharacteristicLevel.HIGH);
    }

    private String joinWithAnd(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + andParticle(names.get(0)) + names.get(1);
        }

        return String.join(", ", names.subList(0, names.size() - 1))
                + andParticle(names.get(names.size() - 2))
                + names.get(names.size() - 1);
    }

    private String subjectParticle(String text) {
        return hasFinalConsonant(text) ? "은" : "는";
    }

    private String andParticle(String text) {
        return hasFinalConsonant(text) ? "과 " : "와 ";
    }

    private boolean hasFinalConsonant(String text) {
        char last = text.charAt(text.length() - 1);
        return last >= '\uAC00' && last <= '\uD7A3' && (last - '\uAC00') % 28 != 0;
    }

    private record LevelGroup(String names, ShoeCharacteristicLevel level) {
    }
}
