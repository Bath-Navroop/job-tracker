package com.navroopbath.jobtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseSchemaTests {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void migrationsCreateUsersAndApplicationsTables() {
        List<String> tables = jdbc.sql("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """)
                .query(String.class)
                .list();

        assertThat(tables).contains("users", "applications");
    }

    @Test
    void emailMustBeUnique() {
        insertUser("nav@example.com");

        assertThatThrownBy(() -> insertUser("nav@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailMustBeStoredLowercase() {
        assertThatThrownBy(() -> insertUser("Nav@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newApplicationStartsAsSaved() {
        long userId = insertUser("nav@example.com");

        String status = jdbc.sql("""
                INSERT INTO applications (user_id, company, job_title)
                VALUES (?, 'Acme', 'Software Engineer')
                RETURNING status
                """)
                .param(userId)
                .query(String.class)
                .single();

        assertThat(status).isEqualTo("SAVED");
    }

    @Test
    void statusMustBeOneOfTheKnownValues() {
        long userId = insertUser("nav@example.com");

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO applications (user_id, company, job_title, status)
                VALUES (?, 'Acme', 'Software Engineer', 'GHOSTED')
                """)
                .param(userId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserDeletesTheirApplications() {
        long userId = insertUser("nav@example.com");
        jdbc.sql("""
                INSERT INTO applications (user_id, company, job_title)
                VALUES (?, 'Acme', 'Software Engineer')
                """)
                .param(userId)
                .update();

        jdbc.sql("DELETE FROM users WHERE id = ?").param(userId).update();

        Integer remaining = jdbc.sql("SELECT count(*) FROM applications WHERE user_id = ?")
                .param(userId)
                .query(Integer.class)
                .single();
        assertThat(remaining).isZero();
    }

    private long insertUser(String email) {
        return jdbc.sql("""
                INSERT INTO users (email, password_hash)
                VALUES (?, 'not-a-real-hash')
                RETURNING id
                """)
                .param(email)
                .query(Long.class)
                .single();
    }
}
