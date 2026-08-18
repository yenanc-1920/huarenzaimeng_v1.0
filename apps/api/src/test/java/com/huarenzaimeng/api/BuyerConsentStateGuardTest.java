package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuyerConsentStateGuardTest {
    @Test void rotatedPolicyFailsClosedBeforeAnyTransactionWrite() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);ResultSet rs=mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("VALID");when(rs.getString(2)).thenReturn("UA-V1");when(rs.getString(3)).thenReturn("PP-V1");
        when(jdbc.query(anyString(),any(RowMapper.class),eq("BUYER-SUBJECT"))).thenAnswer(invocation->{
            RowMapper<?> mapper=invocation.getArgument(1);return List.of(mapper.mapRow(rs,0));
        });
        BuyerConsentStateGuard guard=new BuyerConsentStateGuard(jdbc,"UA-V2","PP-V1");
        assertThatThrownBy(()->guard.requireTransactionWrite("BUYER-SUBJECT"))
                .isInstanceOf(FlowRejectedException.class).hasMessage("BUYER_CONSENT_STALE");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
