package com.huarenzaimeng.api.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real application component graph with the exact CloudBase DEV
 * profile pair. Database runners are replaced because this is an assembly
 * smoke test, not a migration or database test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "HZ_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/huarenzaimeng_dev",
        "SPRING_DATASOURCE_USERNAME=smoke_app",
        "SPRING_DATASOURCE_PASSWORD=smoke_password",
        "SPRING_FLYWAY_USER=smoke_migrator",
        "SPRING_FLYWAY_PASSWORD=smoke_password",
        "HZ_DEV_DATABASE_NAME=huarenzaimeng_dev",
        "HZ_DEV_FUNCTION_RELEASE_ENABLED=true",
        "HZ_ADMIN_BOOTSTRAP_ENABLED=false"
})
@ActiveProfiles({"release-mysql", "local-mysql"})
class DevProfileApplicationSmokeTest {
    @Autowired ApplicationContext context;

    @MockBean DataSource dataSource;
    @MockBean(name = "releaseFlyway") Flyway releaseFlyway;
    @MockBean ProdFlywayBootstrapRunner environmentFlywayBootstrapRunner;
    @MockBean ReleaseMigrationReadyVerifier releaseMigrationReadyVerifier;

    @Test void exactCloudBaseDevelopmentProfilePairLoadsTheApplicationGraph() {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().getActiveProfiles())
                .containsExactly("release-mysql", "local-mysql");
    }
}
