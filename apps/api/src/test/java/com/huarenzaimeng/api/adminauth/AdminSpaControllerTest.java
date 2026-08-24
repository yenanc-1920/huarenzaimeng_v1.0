package com.huarenzaimeng.api.adminauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminSpaControllerTest {
    private final AdminSpaController controller = new AdminSpaController();

    @Test
    void servesIndependentLoginAndInitializationRoutesFromTheSpa() {
        assertEquals("forward:/index.html", controller.loginPage());
        assertEquals("forward:/index.html", controller.initializationPage());
        assertEquals("forward:/index.html", controller.recoveryPage());
    }
}
