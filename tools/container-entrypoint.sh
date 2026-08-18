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

exec java -jar /app/app.jar
