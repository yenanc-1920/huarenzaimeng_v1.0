package com.huarenzaimeng.api;

import com.huarenzaimeng.core.ProjectEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@RestController
@Profile("release-mysql")
@RequestMapping("/api/v1/directory")
final class V1DirectoryController {
    private static final Set<String> REPORT_REASONS=Set.of("INCORRECT_INFO","CLOSED","PHONE_INVALID","ADDRESS_INVALID","OTHER");
    private final V1DevelopmentDataService data;
    private final JdbcTemplate jdbc;
    V1DirectoryController(V1DevelopmentDataService data,JdbcTemplate jdbc) { this.data = data; this.jdbc=jdbc; }

    @GetMapping("/cities") ResponseEntity<?> cities() { return ok(data.cities()); }
    @GetMapping("/entries") ResponseEntity<?> entries(@RequestParam(required=false) String cityCode,
                                                        @RequestParam(required=false) String category) {
        return ok(data.directory(cityCode, category));
    }
    @GetMapping("/entries/{entryRef}") ResponseEntity<?> detail(@PathVariable String entryRef) {
        try { return ok(data.directoryDetail(entryRef)); }
        catch (EmptyResultDataAccessException absent) { return ResponseEntity.notFound().build(); }
    }
    @PostMapping("/entries/{entryRef}/reports") @Transactional ResponseEntity<?> report(
            @PathVariable String entryRef,@RequestHeader("Idempotency-Key") String idempotency,
            @RequestBody DirectoryReport request) {
        if(!safe(entryRef,64)||!safe(idempotency,128)||request==null||!REPORT_REASONS.contains(request.reasonCode())
                ||(request.description()!=null&&request.description().length()>500))
            return ResponseEntity.badRequest().body(new ProjectEnvelope<>("REJECTED","DIRECTORY_REPORT_INPUT_INVALID",null));
        String description=request.description()==null?"":request.description().strip();
        String digest=sha256(entryRef+"\n"+request.reasonCode()+"\n"+description);
        String reportRef="DR-"+sha256(idempotency).substring(0,32);
        try {
            jdbc.update("INSERT INTO hz_directory_report(report_ref,idempotency_key,entry_ref,reason_code,description,request_digest,report_state,created_at) VALUES(?,?,?,?,?,?,'OPEN',?)",
                    reportRef,idempotency,entryRef,request.reasonCode(),description,digest,Timestamp.from(Instant.now()));
        } catch(DuplicateKeyException duplicate) {
            Map<String,Object> existing=jdbc.queryForMap("SELECT report_ref,entry_ref,reason_code,request_digest FROM hz_directory_report WHERE idempotency_key=?",idempotency);
            if(!entryRef.equals(existing.get("entry_ref"))||!request.reasonCode().equals(existing.get("reason_code"))||!digest.equals(existing.get("request_digest")))
                return ResponseEntity.status(409).header("Cache-Control","no-store").body(new ProjectEnvelope<>("REJECTED","IDEMPOTENCY_CONFLICT",null));
            reportRef=String.valueOf(existing.get("report_ref"));
        }
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(Map.of("reportRef",reportRef,"state","OPEN")));
    }
    record DirectoryReport(String reasonCode,String description) {}
    private static boolean safe(String value,int max){return value!=null&&value.length()<=max&&value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,"+(max-1)+"}");}
    private static String sha256(String value){try{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(body));
    }
}
