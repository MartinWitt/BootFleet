package io.github.martinwitt.notesapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class LiquibaseMigrationTest {

    @MockitoBean ChatClient chatClient;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseChangelogHasAllChangesets() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    @Test
    void notesTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM notes"))
                .doesNotThrowAnyException();
    }

    @Test
    void noteTagsTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM note_tags"))
                .doesNotThrowAnyException();
    }

    @Test
    void notesNoteTagsJunctionTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM notes_note_tags"))
                .doesNotThrowAnyException();
    }

    @Test
    void notesTableHasUpdatedAtColumn() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT updated_at FROM notes"))
                .doesNotThrowAnyException();
    }
}
