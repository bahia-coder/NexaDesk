package com.nexadesk.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.ai.HeuristicClassifier.CategoryOption;
import com.nexadesk.ai.HeuristicClassifier.Result;
import com.nexadesk.ai.HeuristicClassifier.SubOption;
import com.nexadesk.category.Level;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicClassifierTest {

    private static final CategoryOption PRINTING = new CategoryOption(1L, "Impression", null, "printer",
            List.of("imprimante", "impression", "toner", "bourrage", "papier"),
            List.of(new SubOption(10L, "Imprimante"), new SubOption(11L, "Consommables")));
    private static final CategoryOption NETWORK = new CategoryOption(2L, "Réseau & VPN", null, "wifi",
            List.of("réseau", "wifi", "vpn", "connexion", "internet"),
            List.of(new SubOption(20L, "VPN"), new SubOption(21L, "Wi-Fi")));
    private static final CategoryOption ACCOUNTS = new CategoryOption(3L, "Comptes & accès", null, "key",
            List.of("mot de passe", "compte", "verrouillé"), List.of());
    private static final List<CategoryOption> CATEGORIES = List.of(PRINTING, NETWORK, ACCOUNTS);

    @Test
    void picksTheCategoryWithTheMostKeywordsIgnoringCaseAccentsAndPlurals() {
        Result result = HeuristicClassifier.classify("Les IMPRIMANTES font des bourrages",
                "Le papier se coince.", CATEGORIES);

        assertThat(result.category()).isEqualTo(PRINTING);
        assertThat(result.subcategory().id()).isEqualTo(10L);
        assertThat(result.confidence()).isBetween(0.5, 0.9);
        assertThat(result.reasons().getFirst()).contains("imprimante", "bourrage", "papier");
    }

    @Test
    void multiWordKeywordsAndAccentsMatch() {
        Result result = HeuristicClassifier.classify("Compte verrouille", "Mon mot de passe ne marche plus",
                CATEGORIES);

        assertThat(result.category()).isEqualTo(ACCOUNTS);
        assertThat(result.subcategory()).isNull();
    }

    @Test
    void wordingDrivesUrgencyAndImpact() {
        Result blocking = HeuristicClassifier.classify("VPN en panne",
                "Toute l'équipe est bloquée depuis ce matin.", CATEGORIES);
        assertThat(blocking.urgency()).isEqualTo(Level.HIGH);
        assertThat(blocking.impact()).isEqualTo(Level.HIGH);
        assertThat(blocking.subcategory().name()).isEqualTo("VPN");

        Result calm = HeuristicClassifier.classify("Toner à prévoir", "Pas urgent, quand vous pourrez.", CATEGORIES);
        assertThat(calm.reasons()).noneMatch(reason -> reason.contains("Urgence élevée"));
        assertThat(calm.urgency()).isEqualTo(Level.LOW);
        assertThat(calm.impact()).isEqualTo(Level.LOW);

        Result neutral = HeuristicClassifier.classify("Wifi lent", "Plusieurs collègues ont le souci.", CATEGORIES);
        assertThat(neutral.urgency()).isEqualTo(Level.MEDIUM);
        assertThat(neutral.impact()).isEqualTo(Level.MEDIUM);
    }

    @Test
    void noKeywordMeansNoCategoryAndZeroConfidence() {
        Result result = HeuristicClassifier.classify("Question générale", "Bonjour, une question.", CATEGORIES);

        assertThat(result.category()).isNull();
        assertThat(result.confidence()).isZero();
        assertThat(result.reasons().getFirst()).contains("Aucun mot-clé");
    }

    @Test
    void aTieLowersTheConfidence() {
        Result tie = HeuristicClassifier.classify("Imprimante réseau", "", CATEGORIES);
        Result clear = HeuristicClassifier.classify("Imprimante", "", CATEGORIES);

        assertThat(tie.confidence()).isLessThan(clear.confidence());
        assertThat(tie.reasons()).anyMatch(reason -> reason.contains("même score"));
    }

    @Test
    void wordStartsOnlyNoMatchInsideAWord() {
        // Keywords match the start of a word: "compte" matches "comptes" but not "décompte".
        Result result = HeuristicClassifier.classify("Décompte des congés", "", CATEGORIES);
        assertThat(result.category()).isNull();
    }
}
