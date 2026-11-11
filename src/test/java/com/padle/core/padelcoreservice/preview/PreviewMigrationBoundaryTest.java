package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewMigrationBoundaryTest {
    @Test
    void wrongMarkerPreventsMigrationInitialization() throws Exception {
        verifyBoundary(false);
    }

    @Test
    void correctMarkerAllowsMigrationInitialization() throws Exception {
        verifyBoundary(true);
    }

    private void verifyBoundary(boolean valid) throws Exception {
        var marker = UUID.randomUUID();
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn(valid ? marker : UUID.randomUUID());
        var migrated = new AtomicBoolean();
        new ApplicationContextRunner().withUserConfiguration(PreviewConfiguration.class)
                .withPropertyValues("spring.profiles.active=preview", "app.preview.instance-id=" + marker)
                .withBean(DataSource.class, () -> source)
                .withBean(PreviewFixtureService.class, () -> mock(PreviewFixtureService.class))
                .withBean("liquibase", InitializingBean.class, () -> () -> migrated.set(true))
                .run(context -> {
                    assertThat(migrated.get()).isEqualTo(valid);
                    if (valid) {
                        assertThat(context).hasNotFailed();
                    } else {
                        assertThat(context).hasFailed();
                    }
                });
    }
}
