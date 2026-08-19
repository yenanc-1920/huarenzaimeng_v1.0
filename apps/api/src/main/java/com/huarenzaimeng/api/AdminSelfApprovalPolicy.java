package com.huarenzaimeng.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("release-mysql")
final class AdminSelfApprovalPolicy {
    private final JdbcTemplate jdbc;private final boolean enabled;private final String policyVersion;
    AdminSelfApprovalPolicy(JdbcTemplate jdbc,@Value("${hz.admin.self-approval.enabled:false}") boolean enabled,
                            @Value("${hz.admin.self-approval.policy-version:}") String policyVersion){this.jdbc=jdbc;this.enabled=enabled;this.policyVersion=policyVersion;}
    Decision decide(String actorUserId,boolean sameActor){
        if(!sameActor)return Decision.separated();
        if(!enabled||policyVersion==null||!policyVersion.matches("[A-Za-z0-9._:-]{3,64}"))throw new PolicyConflict("SELF_APPROVAL_DISABLED");
        List<String> active=jdbc.queryForList("SELECT user_id FROM admin_user WHERE role_code='SUPER_ADMIN' AND status_code='ACTIVE' ORDER BY user_id FOR UPDATE",String.class);
        if(active.size()!=1||!active.get(0).equals(actorUserId))throw new PolicyConflict("SELF_APPROVAL_UNIQUE_SUPER_REQUIRED");
        return new Decision(true,policyVersion);
    }
    record Decision(boolean selfApproved,String exceptionPolicyVersion){static Decision separated(){return new Decision(false,null);}}
    static final class PolicyConflict extends RuntimeException{PolicyConflict(String code){super(code);}}
}
