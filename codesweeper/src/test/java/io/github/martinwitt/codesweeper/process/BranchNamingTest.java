package io.github.martinwitt.codesweeper.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import org.junit.jupiter.api.Test;

class BranchNamingTest {

    @Test
    void isDeterministicForTheSameFindingAndModel() {
        SarifFinding finding = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 42);

        assertThat(BranchNaming.branchFor(finding, "qwen2.5-coder:3b"))
                .isEqualTo(BranchNaming.branchFor(finding, "qwen2.5-coder:3b"));
    }

    @Test
    void differsWhenLocationDiffers() {
        SarifFinding a = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 42);
        SarifFinding b = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 43);

        assertThat(BranchNaming.branchFor(a, "qwen2.5-coder:3b"))
                .isNotEqualTo(BranchNaming.branchFor(b, "qwen2.5-coder:3b"));
    }

    @Test
    void differsWhenModelDiffers() {
        SarifFinding finding = new SarifFinding("IgnoreResultOfCall", "msg", "a/b/File.java", 42);

        assertThat(BranchNaming.branchFor(finding, "qwen2.5-coder:3b"))
                .isNotEqualTo(BranchNaming.branchFor(finding, "gemma4:12b"));
    }

    @Test
    void slugifiesRuleIdAndPrefixesWithCodesweeper() {
        SarifFinding finding = new SarifFinding("Ignore ResultOfCall!", "msg", "a/b/File.java", 1);

        assertThat(BranchNaming.branchFor(finding, "qwen2.5-coder:3b"))
                .startsWith("codesweeper/ignore-resultofcall-");
    }
}
