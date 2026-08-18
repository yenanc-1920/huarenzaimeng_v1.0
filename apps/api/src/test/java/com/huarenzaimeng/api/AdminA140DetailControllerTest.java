package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminA140DetailControllerTest {
    private final AdminReadService read=mock(AdminReadService.class);
    private final AdminA140DetailService details=mock(AdminA140DetailService.class);
    private final AdminReadController controller=new AdminReadController(read,details);

    @Test void finCsAndSuperCanReadButContentCannot() {
        var dto=new AdminA140DetailService.A140Detail("ADMIN_READ_V1","A140-DETAIL-1","A140","FIN",
                null,AdminA140DetailService.PaymentFact.unknown(),AdminA140DetailService.TopupFact.unknown(),
                List.of(),List.of(),List.of(),List.of());
        for(String role:List.of("FIN","CS","SUPER_ADMIN")) {
            when(details.read("O-1",role)).thenReturn(Optional.of(dto));
            MockHttpServletRequest request=new MockHttpServletRequest(); request.setAttribute(AdminSessionFilter.TRUSTED_ROLE,role);
            assertThat(controller.a140Detail("O-1",request).getStatusCode().value()).isEqualTo(200);
        }
        MockHttpServletRequest denied=new MockHttpServletRequest(); denied.setAttribute(AdminSessionFilter.TRUSTED_ROLE,"CONTENT");
        assertThat(controller.a140Detail("O-1",denied).getStatusCode().value()).isEqualTo(403);
    }

    @Test void missingIs404AndUnexpectedInputIs400() {
        when(details.read("O-404","CS")).thenReturn(Optional.empty());
        MockHttpServletRequest request=new MockHttpServletRequest(); request.setAttribute(AdminSessionFilter.TRUSTED_ROLE,"CS");
        assertThat(controller.a140Detail("O-404",request).getStatusCode().value()).isEqualTo(404);
        request.addParameter("debug","true");
        assertThat(controller.a140Detail("O-404",request).getStatusCode().value()).isEqualTo(400);
    }
}
