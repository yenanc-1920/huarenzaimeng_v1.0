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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "HZ_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/huarenzaimeng_test",
        "SPRING_DATASOURCE_USERNAME=smoke_app",
        "SPRING_DATASOURCE_PASSWORD=smoke_password",
        "SPRING_FLYWAY_USER=smoke_migrator",
        "SPRING_FLYWAY_PASSWORD=smoke_password",
        "HZ_ENV_DATABASE_NAME=huarenzaimeng_test",
        "HZ_ENV_MIGRATION_ENABLED=true",
        "HZ_ENV_FUNCTION_RELEASE_ENABLED=true",
        "HZ_ADMIN_BOOTSTRAP_ENABLED=false"
})
@ActiveProfiles({"release-mysql", "test-mysql"})
class TestProfileApplicationSmokeTest {
    @Autowired ApplicationContext context;

    @MockBean DataSource dataSource;
    @MockBean(name = "releaseFlyway") Flyway releaseFlyway;
    @MockBean ProdFlywayBootstrapRunner environmentFlywayBootstrapRunner;
    @MockBean ReleaseMigrationReadyVerifier releaseMigrationReadyVerifier;

    @Test void exactCloudBaseTestProfilePairLoadsWithoutDevelopmentData() {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().getActiveProfiles())
                .containsExactly("release-mysql", "test-mysql");
        assertThat(context.getEnvironment().getProperty("hz.v1-dev-data.enabled", Boolean.class))
                .isFalse();
        assertThat(context.getEnvironment().getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration");
    }
}
