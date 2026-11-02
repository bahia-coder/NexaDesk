package com.nexadesk.ai;

import com.nexadesk.category.Level;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Category, impact and urgency suggestion without any model: keywords configured on each category (by the admin)
 * and a few French wording indicators. Pure logic, unit-tested. It is labelled as a heuristic in the UI.
 *
 * <p>Matching ignores case and accents and works on word starts, so "imprimantes" matches the keyword
 * "imprimante" and "bloquée" matches "bloque". Words of the title count double: it is the most specific text.
 */
public final class HeuristicClassifier {

    public record SubOption(Long id, String name) {
    }

    public record CategoryOption(Long id, String name, String description, String icon, List<String> keywords,
                                 List<SubOption> subcategories) {
    }

    public record Result(CategoryOption category, SubOption subcategory, Level impact, Level urgency,
                         double confidence, List<String> reasons) {
    }

    private static final List<String> URGENT = List.of("bloque", "bloquant", "urgent", "urgence", "impossible",
            "ne fonctionne plus", "ne marche plus", "plus rien", "en panne", "critique", "production",
            "virus", "rancon", "pirat", "client attend");
    private static final List<String> NOT_URGENT = List.of("pas urgent", "quand vous pourrez", "pas presse",
            "a l occasion", "souhaiterais", "aimerais", "serait il possible", "demande d");
    private static final List<String> WIDE_IMPACT = List.of("tout le service", "toute l equipe", "tous les postes",
            "tout l etage", "tout le monde", "toute l entreprise", "personne ne peut", "l ensemble",
            "tous les utilisateurs", "tout le batiment");
    private static final List<String> GROUP_IMPACT = List.of("plusieurs", "collegues", "equipe", "service",
            "participants", "salle de reunion", "nous ne", "on ne");
    private static final Set<String> STOP_WORDS = Set.of("de", "des", "du", "la", "le", "les", "et", "ou", "un",
            "une", "d", "l", "a", "en", "par");

    private HeuristicClassifier() {
    }

    public static Result classify(String title, String description, List<CategoryOption> categories) {
        String titleText = normalize(title);
        String fullText = normalize(title + " " + (description == null ? "" : description));
        List<String> reasons = new ArrayList<>();

        CategoryOption best = null;
        List<String> bestHits = List.of();
        int bestScore = 0;
        int secondScore = 0;
        for (CategoryOption category : categories) {
            List<String> hits = new ArrayList<>();
            int score = 0;
            for (String keyword : category.keywords()) {
                String k = normalize(keyword).strip();
                if (k.isEmpty() || !containsWordStart(fullText, k)) {
                    continue;
                }
                hits.add(keyword.strip());
                score += containsWordStart(titleText, k) ? 2 : 1;
            }
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = category;
                bestHits = hits;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        SubOption subcategory = null;
        double confidence = 0;
        if (best != null) {
            reasons.add("Mots-clés reconnus pour « " + best.name() + " » : " + String.join(", ", bestHits) + ".");
            subcategory = bestSubcategory(best, fullText);
            confidence = Math.min(0.9, 0.35 + 0.12 * bestScore);
            if (secondScore == bestScore) {
                confidence -= 0.2;
                reasons.add("Une autre catégorie obtient le même score : vérifiez le choix.");
            }
            confidence = Math.round(Math.max(0.1, confidence) * 100) / 100.0;
        } else {
            reasons.add("Aucun mot-clé de catégorie reconnu : choisissez la catégorie vous-même.");
        }

        String calm = firstMatch(fullText, NOT_URGENT);
        // "pas urgent" must not count as "urgent": calm phrases are removed before looking for urgency words.
        String withoutCalm = fullText;
        for (String phrase : NOT_URGENT) {
            withoutCalm = withoutCalm.replace(" " + phrase, " ");
        }
        String urgent = firstMatch(withoutCalm, URGENT);
        Level urgency = urgent != null ? Level.HIGH : calm != null ? Level.LOW : Level.MEDIUM;
        if (urgent != null) {
            reasons.add("Urgence élevée : le texte mentionne « " + urgent + " ».");
        } else if (calm != null) {
            reasons.add("Urgence faible : le texte mentionne « " + calm + " ».");
        }

        String wide = firstMatch(fullText, WIDE_IMPACT);
        String group = wide == null ? firstMatch(fullText, GROUP_IMPACT) : null;
        Level impact = wide != null ? Level.HIGH : group != null ? Level.MEDIUM : Level.LOW;
        if (wide != null) {
            reasons.add("Impact élevé : « " + wide + " ».");
        } else if (group != null) {
            reasons.add("Plusieurs personnes semblent concernées : « " + group + " ».");
        }
        return new Result(best, subcategory, impact, urgency, confidence, List.copyOf(reasons));
    }

    /** Subcategory whose name shares the most significant words with the text (none if no word matches). */
    private static SubOption bestSubcategory(CategoryOption category, String text) {
        SubOption best = null;
        long bestMatches = 0;
        for (SubOption sub : category.subcategories()) {
            long matches = significantWords(sub.name()).stream().filter(word -> containsWordStart(text, word)).count();
            if (matches > bestMatches) {
                best = sub;
                bestMatches = matches;
            }
        }
        return best;
    }

    private static Set<String> significantWords(String name) {
        Set<String> words = new LinkedHashSet<>(Arrays.asList(normalize(name).strip().split(" ")));
        words.removeIf(word -> word.length() < 2 || STOP_WORDS.contains(word));
        return words;
    }

    private static String firstMatch(String text, List<String> indicators) {
        return indicators.stream().filter(indicator -> containsWordStart(text, indicator)).findFirst().orElse(null);
    }

    /** {@code text} is normalized and padded with spaces; {@code phrase} is normalized. */
    private static boolean containsWordStart(String text, String phrase) {
        return text.contains(" " + phrase);
    }

    /** Lower case, no accents, punctuation → spaces, single spaces, padded: " l imprimante est bloquee ". */
    static String normalize(String value) {
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return " " + noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip() + " ";
    }
}
