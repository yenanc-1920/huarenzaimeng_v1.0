package com.huarenzaimeng.api.reloadly;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadlySandboxTopupControllerTest {
    @Test void exposesOnlyCreateReadAndActiveStatusForSuperAdmin() {
        ReloadlySandboxTopupServiceTest.MemoryStore store=new ReloadlySandboxTopupServiceTest.MemoryStore();
        ReloadlySandboxTopupPort port=new ReloadlySandboxTopupPort(){
            public CreateResult create(String ignored){return new Created(8001,new java.math.BigDecimal("1.00"),"USD");}
            public StatusResult status(long ignored){return new Observed("PROCESSING");}};
        ReloadlySandboxTopupController controller=new ReloadlySandboxTopupController(new ReloadlySandboxTopupService(port,store));
        MockHttpServletRequest admin=new MockHttpServletRequest();admin.setAttribute(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN");
        var created=controller.create(admin,new ReloadlySandboxTopupController.CreateRequest("RLD-A-20260814-004"));
        var read=controller.read(admin,"RLD-A-20260814-004");
        var status=controller.status(admin,"RLD-A-20260814-004");
        assertThat(created.getStatusCode().value()).isEqualTo(201);assertThat(read.getStatusCode().value()).isEqualTo(200);assertThat(status.getStatusCode().value()).isEqualTo(200);
        assertThat(created.getHeaders().getCacheControl()).isEqualTo("no-store");assertThat(store.statusWrites).isEqualTo(1);
    }

    @Test void nonSuperAdminCannotCallAnyOperation() {
        ReloadlySandboxTopupController controller=new ReloadlySandboxTopupController(null);MockHttpServletRequest request=new MockHttpServletRequest();
        assertThat(controller.create(request,new ReloadlySandboxTopupController.CreateRequest("RLD-A-20260814-005")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.read(request,"RLD-A-20260814-005").getStatusCode().value()).isEqualTo(403);
        assertThat(controller.status(request,"RLD-A-20260814-005").getStatusCode().value()).isEqualTo(403);
    }
}

