package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedTestSessionCookieFilterProfileTest {
    @Test void releaseMysqlNeverPhysicallyRegistersTheSyntheticSessionFilter() {
        Profile profile=TrustedTestSessionCookieFilter.class.getAnnotation(Profile.class);
        assertThat(profile.value()).containsExactly("!release-mysql & (mock | test)");
        try(var release=context("release-mysql")){assertThat(release.getBeansOfType(TrustedTestSessionCookieFilter.class)).isEmpty();}
        try(var mixed=context("release-mysql","test")){assertThat(mixed.getBeansOfType(TrustedTestSessionCookieFilter.class)).isEmpty();}
        try(var mixed=context("release-mysql","mock")){assertThat(mixed.getBeansOfType(TrustedTestSessionCookieFilter.class)).isEmpty();}
        try(var mock=context("mock")){assertThat(mock.getBeansOfType(TrustedTestSessionCookieFilter.class)).hasSize(1);}
    }
    private static AnnotationConfigApplicationContext context(String...profiles){
        var context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles(profiles);
        context.register(TrustedTestSessionCookieFilter.class);context.refresh();return context;
    }
}
