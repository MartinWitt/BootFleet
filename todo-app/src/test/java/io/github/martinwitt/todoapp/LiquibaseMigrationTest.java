package io.github.martinwitt.todoapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class LiquibaseMigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseChangelogTableExistsWithAllChangesets() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    void tagsTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM tags"))
                .doesNotThrowAnyException();
    }

    @Test
    void todosTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM todos"))
                .doesNotThrowAnyException();
    }

    @Test
    void todoTagsTableExists() {
        assertThatCode(() -> jdbcTemplate.queryForList("SELECT * FROM todo_tags"))
                .doesNotThrowAnyException();
    }
}
