package io.github.martinwitt.codesweeper.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import org.junit.jupiter.api.Test;

class BranchNamingTest {

    @Test
    void isDeterministicForTheSameFinding() {
        SarifFinding finding = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 42);

        assertThat(BranchNaming.branchFor(finding)).isEqualTo(BranchNaming.branchFor(finding));
    }

    @Test
    void differsWhenLocationDiffers() {
        SarifFinding a = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 42);
        SarifFinding b = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 43);

        assertThat(BranchNaming.branchFor(a)).isNotEqualTo(BranchNaming.branchFor(b));
    }

    @Test
    void slugifiesRuleIdAndPrefixesWithCodesweeper() {
        SarifFinding finding = new SarifFinding("Ignore ResultOfCall!", "msg", "a/b/File.java", 1);

        assertThat(BranchNaming.branchFor(finding)).startsWith("codesweeper/ignore-resultofcall-");
    }
}
