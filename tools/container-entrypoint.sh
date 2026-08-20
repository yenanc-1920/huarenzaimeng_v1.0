#!/bin/sh
set -eu

profile="${SPRING_PROFILES_ACTIVE:-}"
case "$profile" in
  release-mysql,local-mysql|release-mysql,test-mysql|release-mysql,stage-mysql|release-mysql,prod-mysql)
    ;;
  *)
    echo "Unsupported or missing SPRING_PROFILES_ACTIVE release combination" >&2
    exit 64
    ;;
esac

base_truststore="${HZ_JAVA_TRUSTSTORE_PATH:-/app/truststore/cacerts}"
if [ ! -r "$base_truststore" ] || [ ! -s "$base_truststore" ]; then
  echo "Configured JVM truststore is missing or unreadable" >&2
  exit 78
fi
if ! keytool -list -keystore "$base_truststore" -storepass changeit >/dev/null 2>&1; then
  echo "Configured JVM truststore failed validation" >&2
  exit 78
fi

runtime_truststore="${HZ_JAVA_RUNTIME_TRUSTSTORE_PATH:-/tmp/huaren-java-truststore/cacerts}"
case "$runtime_truststore" in
  /tmp/*) ;;
  *)
    echo "Runtime JVM truststore must be under /tmp" >&2
    exit 78
    ;;
esac

runtime_truststore_dir="${runtime_truststore%/*}"
mkdir -p "$runtime_truststore_dir"
cp "$base_truststore" "$runtime_truststore"
chmod 0600 "$runtime_truststore"

runtime_ca_count=0
system_ca_count=0
import_certificate_directory() {
  source_dir="$1"
  source_kind="$2"
  [ -d "$source_dir" ] || return 0
  for certificate in "$source_dir"/*.crt "$source_dir"/*.cer "$source_dir"/*.pem; do
    [ -f "$certificate" ] || continue
    case "$source_kind" in
      runtime)
        runtime_ca_count=$((runtime_ca_count + 1))
        source_count="$runtime_ca_count"
        ;;
      system)
        system_ca_count=$((system_ca_count + 1))
        source_count="$system_ca_count"
        ;;
      *)
        echo "Unsupported runtime CA source" >&2
        exit 78
        ;;
    esac
    if ! keytool -importcert -noprompt -trustcacerts \
      -alias "huaren-${source_kind}-ca-${source_count}" \
      -file "$certificate" \
      -keystore "$runtime_truststore" \
      -storepass changeit >/dev/null 2>&1; then
      echo "Runtime CA import failed for source $source_kind" >&2
      exit 78
    fi
  done
}

import_certificate_directory "${HZ_RUNTIME_CA_DIRECTORY:-/certificates}" runtime
import_certificate_directory "${HZ_SYSTEM_CA_DIRECTORY:-/usr/local/share/ca-certificates}" system

if ! keytool -list -keystore "$runtime_truststore" -storepass changeit >/dev/null 2>&1; then
  echo "Runtime JVM truststore failed validation" >&2
  exit 78
fi

export HZ_JAVA_TRUSTSTORE_PATH="$runtime_truststore"
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$runtime_truststore -Djavax.net.ssl.trustStorePassword=changeit"
total_imported=$((runtime_ca_count + system_ca_count))
echo "JVM_RUNTIME_TRUSTSTORE_READY runtime_ca_count=$runtime_ca_count system_ca_count=$system_ca_count total_imported=$total_imported"

exec java -jar /app/app.jar
