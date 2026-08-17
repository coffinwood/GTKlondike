#!/bin/sh
# One-off diagnostic launcher for chasing the intermittent native-memory-corruption crash
# ("malloc(): unaligned tcache chunk detected" et al. in GTKlondike.log). NOT meant for
# everyday play - see run-gtklondike.sh for that.
#
# Sets G_DEBUG=fatal-criticals, which turns every GLib/GTK/GDK "CRITICAL" runtime check (e.g.
# "assertion '...' failed") into an immediate abort right at the point the invariant was
# violated, instead of letting corrupted native state limp along until some unrelated
# allocation trips over it minutes/hours later with an unhelpful "malloc(): unaligned tcache
# chunk detected". Confirmed one such abort live on 2026-08-17: "g_atomic_ref_count_dec:
# assertion 'old_value > 0' failed", the same one already seen (non-fatally) in
# build/libs/GTKlondike.log on several earlier dates. Same broad bug family as the already-fixed
# GskTransform/CardWidget native-memory bugs (see git log/comments on PileLayoutManager.java and
# CardWidget.java) - a native GTK object getting freed by Java's GC before GTK's own C code was
# done with it.
#
# Deliberately fatal-criticals, not fatal-warnings: with GTKlondike.main()'s
# prctl(PR_SET_DUMPABLE, 0) now in effect (see below), GTK's portal-monitor setup logs a
# plain (non-critical) "Gtk-WARNING: Creating a portal monitor failed: ... Unable to open
# /proc/<pid>/root" - an expected, harmless side effect of that prctl call, not a bug. Under
# fatal-warnings that single startup warning would abort the app before you could even click
# anything, so this only promotes CRITICALs (the actual assertion-failure bugs) to fatal.
#
# Also note: GTKlondike.main() calls prctl(PR_SET_DUMPABLE, 0) on startup so a crash here never
# hands off to Ubuntu's apport (which was found to embed a full ~200MB memory dump in its crash
# report regardless of `ulimit -c 0` - apport churning through that write is what "the app just
# froze, had to force-quit it" turned out to actually be). So an abort under this script should
# now be a clean, fast, non-frozen exit - if it instead hangs, that's new information worth
# reporting on its own.
#
# If the game aborts while running under this script, send back:
#  - this script's log file (has the specific GLib CRITICAL message that triggered it)
#  - roughly what you were doing in the seconds before (e.g. "drew from stock, then
#    right-clicked two tableau cards in quick succession")
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$SCRIPT_DIR/build/libs/GTKlondike-all.jar"
LOG="$SCRIPT_DIR/build/libs/GTKlondike-debug.log"

ulimit -c 0
cd "$(dirname -- "$JAR")" || exit 1

GSK_RENDERER=gl
export GSK_RENDERER
G_DEBUG=fatal-criticals
export G_DEBUG

{
    echo "=== $(date -Iseconds) starting GTKlondike DEBUG (pid $$, GSK_RENDERER=$GSK_RENDERER, G_DEBUG=$G_DEBUG) ==="
    exec java --enable-native-access=ALL-UNNAMED -jar "$JAR"
} >> "$LOG" 2>&1
