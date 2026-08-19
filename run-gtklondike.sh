#!/bin/sh
# Launches the packaged fat jar the way the GNOME app-grid entry does, but with:
#  - core dumps disabled (belt-and-suspenders only - see below for the mechanism that actually
#    works), so a repeat of the SIGSEGV crashes recorded in past hs_err_pid*.log files (NVIDIA
#    driver crashing inside GTK's GSK renderer) doesn't have apport stalling for minutes trying
#    to write a multi-GB core dump - which is what a "frozen, had to force-kill it" symptom
#    actually was. NOTE: `ulimit -c 0` alone does NOT stop this - confirmed 2026-08-17, a real
#    SIGABRT still produced a ~200MB apport report in /var/crash despite it. The actual fix is
#    GTKlondike.main() writing "0" to /proc/self/coredump_filter (see core(5)), which tells the
#    kernel to exclude memory contents from any coredump apport builds, without the side effects
#    of the prctl(PR_SET_DUMPABLE, 0) approach tried and reverted earlier (that blocked
#    xdg-desktop-portal's /proc/<pid>/root introspection too, breaking dark-mode/icon theming -
#    see GTKlondike.java's comment on this). This ulimit line is kept only in case that ever
#    gets removed.
#  - stdout/stderr appended to a log file under ~/.local/state/gtklondike/, since a
#    desktop-launched process (no attached terminal) would otherwise just drop that output.
#    Deliberately NOT under build/libs/ (next to the jar) - that's a Gradle build output
#    directory and `./gradlew clean` wipes it, which would lose crash history.
#  - the working directory set to that same log folder, so if the JVM does fatal-crash again,
#    the resulting hs_err_pid*.log lands next to the log instead of wherever GNOME happened to
#    default the cwd to (or getting wiped by a clean build)
#  - java exec'd in place (not run as a child), so this script's pid stays the java pid and
#    `kill -QUIT <pid>` still reaches it directly for a thread dump on a genuine Java-level hang
#  - GSK_RENDERER forced to "gl" (GTK4's older/simpler GL renderer) instead of the default "ngl"
#    one, since the recorded crashes were the NVIDIA driver segfaulting inside "ngl"'s
#    glClientWaitSync call - a known bad interaction on some driver versions
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$SCRIPT_DIR/build/libs/GTKlondike-all.jar"
LOGDIR="$HOME/.local/state/gtklondike"
LOG="$LOGDIR/GTKlondike.log"

mkdir -p "$LOGDIR"
ulimit -c 0
cd "$LOGDIR" || exit 1

GSK_RENDERER=gl
export GSK_RENDERER

{
    echo "=== $(date -Iseconds) starting GTKlondike (pid $$, GSK_RENDERER=$GSK_RENDERER) ==="
    exec java --enable-native-access=ALL-UNNAMED -jar "$JAR"
} >> "$LOG" 2>&1
