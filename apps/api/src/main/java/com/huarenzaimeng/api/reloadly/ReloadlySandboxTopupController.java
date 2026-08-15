package com.huarenzaimeng.api.reloadly;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.reloadly.sandbox-topup.enabled",havingValue="true")
@ConditionalOnBean(ReloadlySandboxTopupService.class)
@RequestMapping("/admin-read/v1/reloadly-sandbox/topups")
final class ReloadlySandboxTopupController {
    private final ReloadlySandboxTopupService service;
    ReloadlySandboxTopupController(ReloadlySandboxTopupService service){this.service=service;}

    @PostMapping ResponseEntity<?> create(HttpServletRequest servlet,@Valid @RequestBody CreateRequest request){
        if(!superAdmin(servlet))return forbidden();
        return ResponseEntity.status(201).header("Cache-Control","no-store").body(service.create(request.requestRef()));
    }
    @GetMapping("/{requestRef}") ResponseEntity<?> read(HttpServletRequest servlet,@PathVariable String requestRef){
        if(!superAdmin(servlet))return forbidden();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.read(requestRef));
    }
    @PostMapping("/{requestRef}/status-queries") ResponseEntity<?> status(HttpServletRequest servlet,@PathVariable String requestRef){
        if(!superAdmin(servlet))return forbidden();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.queryStatus(requestRef));
    }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(){
        return ResponseEntity.badRequest().header("Cache-Control","no-store").body(java.util.Map.of("status","REJECTED","code","REQUEST_INVALID"));
    }
    private static boolean superAdmin(HttpServletRequest request){return "SUPER_ADMIN".equals(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));}
    private static ResponseEntity<Void> forbidden(){return ResponseEntity.status(403).header("Cache-Control","no-store").build();}
    record CreateRequest(@Pattern(regexp="RLD-A-[A-Z0-9-]{8,40}") String requestRef){}
}

