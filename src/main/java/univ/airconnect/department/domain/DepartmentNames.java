package univ.airconnect.department.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DepartmentNames {
    private static final Map<String, String> RENAMES;

    static {
        Map<String, String> renames = new LinkedHashMap<>();
        renames.put("문화재보존학과", "문화유산보존학과");
        renames.put("실용음악과", "뮤직프로덕션학과");
        renames.put("산업디자인학과", "디지털산업디자인학과");
        RENAMES = Map.copyOf(renames);
    }

    private DepartmentNames() {
    }

    public static String canonicalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        return RENAMES.getOrDefault(normalized, normalized);
    }

    public static Map<String, String> renames() {
        return RENAMES;
    }
}
