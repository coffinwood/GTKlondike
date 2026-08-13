#!/usr/bin/env bash
# Re-embeds congratulations.svg as the inline data URI in .victory_banner's
# background-image, and warns if the SVG's viewBox dimensions no longer match
# VICTORY_BANNER_ASPECT_RATIO in GTKlondike.java.
set -euo pipefail

cd "$(dirname "$0")"

SVG=congratulations_2.svg
CSS=src/main/resources/style/gtklondike.css
JAVA=src/main/java/org/coffinwood/gtklondike/GTKlondike.java

python3 - "$SVG" "$CSS" <<'EOF'
import re, sys, urllib.parse

svg_path, css_path = sys.argv[1], sys.argv[2]

with open(svg_path, encoding="utf-8") as f:
    svg = f.read()

# collapse whitespace/newlines to one line, then percent-encode like
# Inkscape's "Edit > Copy Data URI" does
svg = " ".join(svg.split())
encoded = urllib.parse.quote(svg, safe="")

with open(css_path, encoding="utf-8") as f:
    css = f.read()

pattern = re.compile(
    r'(\.victory_banner \{\n    border-width: 2px;\n    background-image: url\("data:image/svg\+xml;charset=utf8,)([^"]*)("\);)'
)
m = pattern.search(css)
if not m:
    sys.exit("error: .victory_banner background-image rule not found in " + css_path)

css = css[:m.start(2)] + encoded + css[m.end(2):]

with open(css_path, "w", encoding="utf-8") as f:
    f.write(css)

print(f"updated {css_path} ({len(m.group(2))} -> {len(encoded)} chars)")

# report current viewBox dims so you can compare against VICTORY_BANNER_ASPECT_RATIO
vb = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg)
if vb:
    print(f"congratulations.svg viewBox: {vb.group(1)} x {vb.group(2)}")
    print("If these changed, update VICTORY_BANNER_ASPECT_RATIO in " + "GTKlondike.java")
EOF
