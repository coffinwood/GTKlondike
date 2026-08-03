#!/bin/sh
# Launches the packaged fat jar the way the GNOME app-grid entry does, but with:
#  - core dumps disabled, so a repeat of the SIGSEGV crashes recorded in past
#    hs_err_pid*.log files (NVIDIA driver crashing inside GTK's GSK renderer) makes the
#    process exit immediately instead of apport stalling for minutes trying to write a
#    multi-GB core dump - which is what a "frozen, had to force-kill it" symptom actually was
#  - stdout/stderr appended to a log file next to the jar, since a desktop-launched process
#    (no attached terminal) would otherwise just drop that output
#  - the working directory set to the jar's own folder, so if the JVM does fatal-crash again,
#    the resulting hs_err_pid*.log lands next to the jar/log instead of wherever GNOME
#    happened to default the cwd to
#  - java exec'd in place (not run as a child), so this script's pid stays the java pid and
#    `kill -QUIT <pid>` still reaches it directly for a thread dump on a genuine Java-level hang
#  - GSK_RENDERER forced to "gl" (GTK4's older/simpler GL renderer) instead of the default "ngl"
#    one, since the recorded crashes were the NVIDIA driver segfaulting inside "ngl"'s
#    glClientWaitSync call - a known bad interaction on some driver versions
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$SCRIPT_DIR/build/libs/GTKlondike-all.jar"
LOG="$SCRIPT_DIR/build/libs/GTKlondike.log"

ulimit -c 0
cd "$(dirname -- "$JAR")" || exit 1

GSK_RENDERER=gl
export GSK_RENDERER

{
    echo "=== $(date -Iseconds) starting GTKlondike (pid $$, GSK_RENDERER=$GSK_RENDERER) ==="
    exec java --enable-native-access=ALL-UNNAMED -jar "$JAR"
} >> "$LOG" 2>&1
