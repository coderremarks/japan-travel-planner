package dev.tabiji.catalog.infrastructure;

import dev.tabiji.catalog.domain.Place;
import dev.tabiji.catalog.domain.PlaceCategory;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Type-safe search predicates. Free-text search uses a bound parameter with an explicit escape
 * character, and {@code %}, {@code _} and the escape character in the user's input are escaped —
 * otherwise a search for "%" would match everything (LIKE-wildcard injection), even though SQL
 * injection itself is impossible with bound parameters.
 */
public final class PlaceSpecifications {

    private static final char ESCAPE = '\\';

    private PlaceSpecifications() {}

    public static Specification<Place> inArea(String areaCode) {
        return (root, query, cb) -> cb.equal(root.get("areaCode"), areaCode);
    }

    public static Specification<Place> inCategory(PlaceCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Place> nameContains(String term) {
        String pattern = "%" + escapeLike(term.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nameEn")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("nameJa")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("slug")), pattern, ESCAPE));
    }

    static String escapeLike(String input) {
        StringBuilder escaped = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
