#!/bin/bash
# Build + run script for Wavestream desktop app
# Compiles Kotlin via gradle, then runs directly with java + classpath

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

echo "=== Compiling Kotlin (desktop target) ==="
./gradlew :shared:compileKotlinDesktop --no-configuration-cache 2>&1 | tail -3

echo ""
echo "=== Getting classpath from gradle ==="
./gradlew :shared:printDesktopClasspath --no-configuration-cache 2>&1 | sed -n '/CLASSPATH_START/,/CLASSPATH_END/p' | grep -v "CLASSPATH_" > /tmp/wavestream_cp.txt
CLASSPATH="shared/build/classes/kotlin/desktop/main"
while IFS= read -r jar; do
    [ -n "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done < /tmp/wavestream_cp.txt

# Also include resources
if [ -d "shared/build/generated/compose/resourceGenerator" ]; then
    CLASSPATH="$CLASSPATH:shared/build/generated/compose/resourceGenerator"
fi

echo "Classpath entries: $(echo $CLASSPATH | tr ':' '\n' | wc -l)"
echo ""

echo "=== Running Wavestream desktop app ==="
java -cp "$CLASSPATH" com.wavestream.MainKt 2>&1
