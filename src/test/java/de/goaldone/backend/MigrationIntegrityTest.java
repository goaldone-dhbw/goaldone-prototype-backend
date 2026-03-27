package de.goaldone.backend;

import de.goaldone.backend.repository.BreakRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests to verify database migrations applied correctly.
 * Tests new columns, constraints, and tables from Step 0-2 schema changes.
 */
public class MigrationIntegrityTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BreakRepository breakRepository;

    @Test
    void migration_breaks_newColumnsExist() {
        // Verify new columns exist in breaks table
        var result = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name='breaks' " +
                "AND column_name IN ('break_type', 'date', 'valid_from', 'valid_until', 'organization_id')"
        );

        assertThat(result).hasSize(5);
    }

    @Test
    void migration_breaks_recurrenceNullable() {
        // Verify recurrence_type and recurrence_interval are nullable
        var result = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name='breaks' " +
                "AND column_name IN ('recurrence_type', 'recurrence_interval') " +
                "AND is_nullable='YES'"
        );

        assertThat(result).hasSize(2);
    }

    @Test
    void migration_breaks_noNullBreakType() {
        // Verify no break_type IS NULL (all breaks have a type)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM breaks WHERE break_type IS NULL",
                Integer.class
        );

        assertThat(count).isEqualTo(0);
    }

    @Test
    void migration_recurringTemplatesTableExists() {
        // Verify recurring_templates table exists
        var result = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name='recurring_templates'"
        );

        assertThat(result).hasSize(1);
    }

    @Test
    void migration_recurringExceptionsTableExists() {
        // Verify recurring_exceptions table exists
        var result = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name='recurring_exceptions'"
        );

        assertThat(result).hasSize(1);
    }

    @Test
    void migration_breaksOrganizationIdNotNull() {
        // Verify organization_id is NOT NULL in breaks table
        var result = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name='breaks' " +
                "AND column_name='organization_id' AND is_nullable='NO'"
        );

        assertThat(result).hasSize(1);
    }
}
