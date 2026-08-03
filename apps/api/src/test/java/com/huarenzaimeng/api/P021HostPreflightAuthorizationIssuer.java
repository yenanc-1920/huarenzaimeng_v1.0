package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Explicitly invoked issuer; ordinary tests never call issue(). */
final class P021HostPreflightAuthorizationIssuer {
    static final String RUN_ID="P021-TECH-HOST-PREFLIGHT-20260803-004";
    static final String AUTHORIZATION_REF="AUTH-P021-TECH-HOST-PREFLIGHT-20260803-004";
    static final String FILE_NAME=AUTHORIZATION_REF+".json";
    static final Path AUTHORIZATION_ROOT=Path.of("项目管理/正式交付/D4-开发计划与工程准备/授权记录/P021-HOST-PREFLIGHT");
    static final Path EVIDENCE_ROOT=Path.of("项目管理/正式交付/D4-开发计划与工程准备/证据/P021-HOST-PREFLIGHT");
    static final String CORE_AGGREGATE_SHA="BCD95CC3FAF338F03315D82BA44ACB4F899829D7F2F3ED3473148DB6B8336E0D";
    static final String FULL_AGGREGATE_SHA="A8F2DADD53F851D8E2EEF5FE8CC06294247453FBEF83A23B8A05CC03B6315276";
    static final String OUTER_COMMAND_DIGEST="3E95160331DE735507FBC53B388B2BE69AC89C22D31B7175FE0B3B929F81446B";
    static final String INNER_COMMAND_DIGEST="F98E3EA836EBC31073E5901E4FAA1FB707A2023C85E45B126AB13C4DD1C1120D";
    static final String IMPLEMENTATION_SHA="C4A8D77A5422354C546473DAF013893952349A447349B3E8CC9F89A884CF0ADE";
    static final String MATRIX_SHA="A0134787E0484705ADE5E32383D778D7851BC2DAD72FE9F6873752CBE639A8DE";
    static final String RUNNER_SHA="7E2B711DF2F4D6621F7A5CD44CA07CC5BB3C032653C61392354205C1214F2B3E";
    private static final ObjectMapper JSON=new ObjectMapper();

    private static final List<String> CORE_PATHS=List.of(
            "apps/api/src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHost.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightOuterWrapper.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightUtf8CompiledLauncher.java");
    private static final List<String> FULL_PATHS=List.of(
            CORE_PATHS.get(0),CORE_PATHS.get(1),CORE_PATHS.get(2),CORE_PATHS.get(3),
            "apps/api/src/test/java/com/huarenzaimeng/api/P021SameChainHostPreflightExecutionTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021SameChainHostPreflightSelectorTest.java",
            "apps/api/manifests/P021-host-offline-repository-identity.txt");
    private static final Map<String,String> TOOL_SHA=Map.of(
            "C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe","B3AFE83E1AB067DA4C56F1A7B2BA4C14EC832D694333F35B2B45178E9AC596EF",
            "C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javac.exe","988446CEEC6E33CE0420DDF2489BB13D3A2278B4C8745309123DFA8F2C0DD0CE",
            "C:/Users/yenanc/tools/apache-maven-3.9.6/boot/plexus-classworlds-2.7.0.jar","C60AE538BA66ADBC06AAE205FBE2306211D3D213AB6DF3239EC03CDDE2458AD6",
            "C:/Users/yenanc/tools/apache-maven-3.9.6/bin/m2.conf","E336769BF93A902BAA7A3E827BA55E4CEF7DE4AF2ED1A9541A4261094D748BA9");

    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new IllegalArgumentException("ISSUER_ARGUMENTS_FORBIDDEN");
        Path target=issue(Path.of(""),Instant.now());
        System.out.println("AuthorizationRef="+AUTHORIZATION_REF);
        System.out.println("AuthorizationRecord="+target.toAbsolutePath().normalize());
        System.out.println("AuthorizationRecordSha="+sha(target));
    }

    static Path issue(Path repository, Instant validFrom) throws Exception {
        Path root=repository.toAbsolutePath().normalize();
        P021HostPreflightRunIdPolicy.validate(RUN_ID);
        validateFixedInputs(root);
        assertFresh(root);
        Instant validUntil=validFrom.plusSeconds(900);
        ObjectNode auth=JSON.createObjectNode();
        auth.put("AuthorizationRef",AUTHORIZATION_REF).put("ExecutionScope",P021ControlledEvidenceJavaHost.PREFLIGHT_SCOPE)
                .put("RunId",RUN_ID).put("ValidFrom",validFrom.toString()).put("ValidUntil",validUntil.toString())
                .put("ImplementationAggregateSha",IMPLEMENTATION_SHA).put("MatrixIdentitySha",MATRIX_SHA)
                .put("RunnerAggregateSha",RUNNER_SHA).put("JavaHostAggregateSha",FULL_AGGREGATE_SHA)
                .put("OuterCommandDigest",OUTER_COMMAND_DIGEST).put("InnerCommandDigest",INNER_COMMAND_DIGEST)
                .put("MavenHome",P021ControlledEvidenceJavaHost.PREFLIGHT_MAVEN_HOME.toAbsolutePath().normalize().toString())
                .put("MavenLauncherPath",P021ControlledEvidenceJavaHost.PREFLIGHT_MAVEN_LAUNCHER.toAbsolutePath().normalize().toString())
                .put("MavenLauncherSha",P021ControlledEvidenceJavaHost.PREFLIGHT_MAVEN_LAUNCHER_SHA)
                .put("MavenConfigPath",P021ControlledEvidenceJavaHost.PREFLIGHT_MAVEN_CONFIG.toAbsolutePath().normalize().toString())
                .put("MavenConfigSha",P021ControlledEvidenceJavaHost.PREFLIGHT_MAVEN_CONFIG_SHA)
                .put("JavaExecutablePath",P021ControlledEvidenceJavaHost.PREFLIGHT_JAVA.toAbsolutePath().normalize().toString())
                .put("JavaExecutableSha",P021ControlledEvidenceJavaHost.PREFLIGHT_JAVA_SHA)
                .put("OuterWrapperPath",P021ControlledEvidenceJavaHost.PREFLIGHT_OUTER_WRAPPER.toString().replace('\\','/'))
                .put("OuterWrapperSha",sha(root.resolve(P021ControlledEvidenceJavaHost.PREFLIGHT_OUTER_WRAPPER)))
                .put("Offline",true).put("OfflineRepositoryPath",P021ControlledEvidenceJavaHost.PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize().toString())
                .put("OfflineRepositoryIdentitySha",P021ControlledEvidenceJavaHost.PREFLIGHT_OFFLINE_REPOSITORY_IDENTITY_SHA)
                .put("OfflineRepositoryManifestPath",P021ControlledEvidenceJavaHost.PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST.toString().replace('\\','/'))
                .put("OfflineRepositoryManifestSha",P021ControlledEvidenceJavaHost.PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST_SHA)
                .put("SingleUse",true).put("AutomaticRetryAllowed",false).put("Status","APPROVED");
        Path authRoot=root.resolve(AUTHORIZATION_ROOT).normalize(), target=authRoot.resolve(FILE_NAME).normalize();
        if(!target.getParent().equals(authRoot))throw new IllegalStateException("AUTHORIZATION_PATH");
        Files.createDirectories(authRoot);
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(auth);
        try(FileChannel channel=FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            channel.write(ByteBuffer.wrap(bytes));channel.force(true);
        }catch(FileAlreadyExistsException duplicate){throw new IllegalStateException("AUTHORIZATION_ALREADY_EXISTS",duplicate);}
        String recordSha=sha(target);
        if(recordSha.isBlank())throw new IllegalStateException("AUTHORIZATION_SHA");
        return target;
    }

    static void validateFixedInputs(Path root)throws Exception{
        if(!aggregate(root,CORE_PATHS).equals(CORE_AGGREGATE_SHA)||!aggregate(root,FULL_PATHS).equals(FULL_AGGREGATE_SHA)
                ||!P021ControlledEvidenceJavaHost.preflightOuterCommandDigest().equals(OUTER_COMMAND_DIGEST)
                ||!P021ControlledEvidenceJavaHost.preflightInnerCommandDigest().equals(INNER_COMMAND_DIGEST))
            throw new IllegalStateException("FIXED_HOST_DRIFT");
        for(var entry:TOOL_SHA.entrySet())if(!sha(Path.of(entry.getKey())).equals(entry.getValue()))throw new IllegalStateException("TOOL_DRIFT");
    }

    static void assertFresh(Path root)throws Exception{
        Path authRoot=root.resolve(AUTHORIZATION_ROOT).normalize(),evidenceRoot=root.resolve(EVIDENCE_ROOT).normalize();
        if(Files.exists(authRoot.resolve(FILE_NAME)))throw new IllegalStateException("AUTHORIZATION_ALREADY_EXISTS");
        for(Path scanRoot:List.of(authRoot,evidenceRoot))if(Files.exists(scanRoot))try(var paths=Files.walk(scanRoot)){
            if(paths.anyMatch(path->path.toString().contains(RUN_ID)))throw new IllegalStateException("RUN_ID_NOT_FRESH");
        }
    }

    private static String aggregate(Path root,List<String>paths)throws Exception{
        var lines=new java.util.ArrayList<String>();for(String path:paths)lines.add(path+"|"+sha(root.resolve(path)));
        return digest(String.join("\n",lines).getBytes(StandardCharsets.UTF_8));
    }
    private static String sha(Path path)throws Exception{return digest(Files.readAllBytes(path));}
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private P021HostPreflightAuthorizationIssuer(){}
}
