package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P021HostPreflightAuthorizationIssuerContractTest {
    @Test void permanentlyRejectsAllPriorRunIds(){
        for(String suffix:java.util.List.of("001","002","003"))
            assertThatThrownBy(()->P021HostPreflightRunIdPolicy.validate("P021-TECH-HOST-PREFLIGHT-20260803-"+suffix))
                    .hasMessage("PREFLIGHT_RUN_ID_INVALID_OR_DENIED");
    }

    @Test void freshnessRejectsAuthorizationAndEveryEvidenceNamespace()throws Exception{
        Path tempParent=Path.of(".tmp").toAbsolutePath().normalize();Files.createDirectories(tempParent);
        Path root=Files.createTempDirectory(tempParent,"p021-issuer-negative-");
        try{
            Path auth=root.resolve(P021HostPreflightAuthorizationIssuer.AUTHORIZATION_ROOT);
            Files.createDirectories(auth);Files.writeString(auth.resolve(P021HostPreflightAuthorizationIssuer.FILE_NAME),"candidate");
            assertThatThrownBy(()->P021HostPreflightAuthorizationIssuer.assertFresh(root)).hasMessage("AUTHORIZATION_ALREADY_EXISTS");
            Files.delete(auth.resolve(P021HostPreflightAuthorizationIssuer.FILE_NAME));
            Path evidence=root.resolve(P021HostPreflightAuthorizationIssuer.EVIDENCE_ROOT);
            for(String prefix:java.util.List.of("",".staging-",".publishing-",".blocked-",".process-")){
                Path marker=evidence.resolve(prefix+P021HostPreflightAuthorizationIssuer.RUN_ID);Files.createDirectories(marker);
                assertThatThrownBy(()->P021HostPreflightAuthorizationIssuer.assertFresh(root)).hasMessage("RUN_ID_NOT_FRESH");
                Files.delete(marker);
            }
            Path consumption=auth.resolve("AUTH.json.consumed."+P021HostPreflightAuthorizationIssuer.RUN_ID+".json");
            Files.writeString(consumption,"consumed");
            assertThatThrownBy(()->P021HostPreflightAuthorizationIssuer.assertFresh(root)).hasMessage("RUN_ID_NOT_FRESH");
        }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
