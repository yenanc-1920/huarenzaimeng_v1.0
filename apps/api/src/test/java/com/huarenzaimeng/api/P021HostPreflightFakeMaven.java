package com.huarenzaimeng.api;
import java.nio.file.*;
public final class P021HostPreflightFakeMaven{
  public static void main(String[]args)throws Exception{
    System.out.println("FAKE_MAVEN_STDOUT");System.err.println("FAKE_MAVEN_STDERR");
    String report=System.getProperty("fake.report");if(report!=null){Path root=Path.of(report);Files.createDirectories(root);Files.writeString(root.resolve("preflight-result.json"),"{}");}
    System.exit(Integer.getInteger("fake.exit",0));
  }
}
