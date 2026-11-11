#!/usr/bin/env bash
# Builds the desktop jar with Gradle.
# Output: desktop/build/libs/Mindustry.jar (or the path given as first argument).
#
# The build only works on JDK 17-21 (newer JDKs break Gradle's build script
# compilation), so if JAVA_HOME is unset or points elsewhere this picks a
# JDK 21 or 17 install from /usr/lib/jvm.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

# Prints the major version of the JDK at $1, e.g. 21 (or 8 for 1.8).
jdk_major(){
    local v
    v="$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n1)"
    v="${v#1.}"
    echo "${v%%[.+-]*}"
}

supported(){
    [[ -x "$1/bin/java" ]] || return 1
    local major
    major="$(jdk_major "$1")"
    [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 17 && major <= 21 ))
}

if [[ -n "${JAVA_HOME:-}" ]] && ! supported "$JAVA_HOME"; then
    echo "JAVA_HOME=$JAVA_HOME is not JDK 17-21, looking for another JDK."
    unset JAVA_HOME
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    for jdk in /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/*; do
        if supported "$jdk"; then
            export JAVA_HOME="$jdk"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "No JDK 17-21 found in /usr/lib/jvm; set JAVA_HOME to one." >&2
    exit 1
fi

echo "Using JAVA_HOME=$JAVA_HOME"
./gradlew desktop:dist

jar="desktop/build/libs/Mindustry.jar"
if [[ $# -ge 1 ]]; then
    cp "$jar" "$1"
    jar="$1"
fi

echo "Built $jar"
