#!/bin/sh
set -e

MAVEN_VERSION="3.9.9"
MAVEN_HOME="${HOME}/.m2/wrapper/apache-maven-${MAVEN_VERSION}"
MAVEN_BIN="${MAVEN_HOME}/bin/mvn"

if [ ! -f "${MAVEN_BIN}" ]; then
    echo "Downloading Apache Maven ${MAVEN_VERSION}..."
    mkdir -p "${MAVEN_HOME}"
    URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    curl -fsSL "${URL}" | tar xz -C "${MAVEN_HOME}" --strip-components=1
fi

exec "${MAVEN_BIN}" "$@"