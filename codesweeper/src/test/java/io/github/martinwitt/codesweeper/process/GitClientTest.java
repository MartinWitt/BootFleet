package io.github.martinwitt.codesweeper.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitClientTest {

    @Mock ProcessRunner processRunner;
    @TempDir Path checkout;

    @Test
    void commitAllSetsBotIdentitySoCommitDoesNotNeedAGlobalGitConfig() {
        GitClient gitClient =
                new GitClient(
                        processRunner,
                        new CodesweeperProperties(
                                "workspace",
                                "sarif.json",
                                "token",
                                "fixer",
                                "judge",
                                List.of(),
                                "rule",
                                "path"));
        when(processRunner.run(any(), any(String[].class))).thenReturn(new ProcessResult(0, ""));

        gitClient.commitAll(checkout, "fix: something");

        ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
        verify(processRunner, org.mockito.Mockito.times(2)).run(any(), commandCaptor.capture());
        String commitCommand = String.join(" ", commandCaptor.getAllValues().get(1));

        assertThat(commitCommand)
                .contains("user.name=codesweeper[bot]")
                .contains("user.email=codesweeper[bot]@users.noreply.github.com");
    }
}
