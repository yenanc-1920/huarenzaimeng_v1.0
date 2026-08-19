#!/bin/sh
set -eu

truststore="${HZ_JAVA_TRUSTSTORE_PATH:-/app/truststore/cacerts}"
if [ ! -r "$truststore" ] || [ ! -s "$truststore" ]; then
  echo "Configured JVM truststore is missing or unreadable" >&2
  exit 78
fi
case "${JAVA_TOOL_OPTIONS:-}" in
  *"-Djavax.net.ssl.trustStore=$truststore"*) ;;
  *)
    echo "JAVA_TOOL_OPTIONS does not bind the configured JVM truststore" >&2
    exit 78
    ;;
esac
if ! keytool -list -keystore "$truststore" -storepass changeit >/dev/null 2>&1; then
  echo "Configured JVM truststore failed validation" >&2
  exit 78
fi

profile="${SPRING_PROFILES_ACTIVE:-}"
case "$profile" in
  release-mysql,local-mysql|release-mysql,test-mysql|release-mysql,stage-mysql|release-mysql,prod-mysql)
    ;;
  *)
    echo "Unsupported or missing SPRING_PROFILES_ACTIVE release combination" >&2
    exit 64
    ;;
esac

exec java -jar /app/app.jar
