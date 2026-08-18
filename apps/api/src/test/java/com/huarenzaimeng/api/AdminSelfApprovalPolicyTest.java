package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSelfApprovalPolicyTest {
    @Test void explicitPolicyAllowsOnlyTheLockedUniqueActiveSuperActor(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FOR UPDATE"),eq(String.class))).thenReturn(List.of("SUPER-ONLY"));
        var decision=new AdminSelfApprovalPolicy(jdbc,true,"SA-EXCEPTION-V1").decide("SUPER-ONLY",true);
        assertThat(decision.selfApproved()).isTrue();assertThat(decision.exceptionPolicyVersion()).isEqualTo("SA-EXCEPTION-V1");
        verify(jdbc).queryForList(contains("role_code='SUPER_ADMIN' AND status_code='ACTIVE'"),eq(String.class));
    }

    @Test void disabledMultipleOrDifferentSuperFailsClosed(){
        JdbcTemplate disabled=mock(JdbcTemplate.class);
        assertThatThrownBy(()->new AdminSelfApprovalPolicy(disabled,false,"SA-EXCEPTION-V1").decide("SUPER-ONLY",true))
                .isInstanceOfSatisfying(AdminSelfApprovalPolicy.PolicyConflict.class,e->assertThat(e.getMessage()).isEqualTo("SELF_APPROVAL_DISABLED"));
        verifyNoInteractions(disabled);
        JdbcTemplate multiple=mock(JdbcTemplate.class);when(multiple.queryForList(anyString(),eq(String.class))).thenReturn(List.of("SUPER-ONLY","SUPER-OTHER"));
        assertThatThrownBy(()->new AdminSelfApprovalPolicy(multiple,true,"SA-EXCEPTION-V1").decide("SUPER-ONLY",true))
                .isInstanceOfSatisfying(AdminSelfApprovalPolicy.PolicyConflict.class,e->assertThat(e.getMessage()).isEqualTo("SELF_APPROVAL_UNIQUE_SUPER_REQUIRED"));
        JdbcTemplate different=mock(JdbcTemplate.class);when(different.queryForList(anyString(),eq(String.class))).thenReturn(List.of("SUPER-OTHER"));
        assertThatThrownBy(()->new AdminSelfApprovalPolicy(different,true,"SA-EXCEPTION-V1").decide("SUPER-ONLY",true))
                .isInstanceOf(AdminSelfApprovalPolicy.PolicyConflict.class);
    }

    @Test void separatedActorsNeedNoExceptionOrDatabaseRead(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);var decision=new AdminSelfApprovalPolicy(jdbc,false,"").decide("CONTENT-REVIEWER",false);
        assertThat(decision.selfApproved()).isFalse();assertThat(decision.exceptionPolicyVersion()).isNull();verifyNoInteractions(jdbc);
    }
}
