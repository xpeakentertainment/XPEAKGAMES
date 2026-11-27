#!/usr/bin/env bash
# Dumps public/protected API signatures (javap) of the classes listed in ci/classes.txt
# and a few member-name mappings needed for reflection. Only signatures, no code.
OUT="$1"
mkdir -p "$OUT"
FG="$HOME/.gradle/caches/forge_gradle"
JAR=$(find "$FG/minecraft_user_repo" -name "forge-*_mapped_official_*.jar" ! -name "*sources*" | head -1)
echo "mapped jar: $JAR" > "$OUT/info.txt"
find "$FG" -maxdepth 8 \( -name "*.tsrg*" -o -name "*.txt" -o -name "*.srg" -o -name "*.zip" \) | head -100 >> "$OUT/info.txt"
while read -r cls; do
  [ -z "$cls" ] && continue
  echo "===== $cls" >> "$OUT/javap.txt"
  javap -protected -cp "$JAR" "$cls" >> "$OUT/javap.txt" 2>&1
done < ci/classes.txt
# mapping lookups for reflection (official -> srg)
if [ -f ci/members.txt ]; then
  for f in $(find "$FG" \( -name "*.tsrg" -o -name "*official*" -o -name "*mappings*.txt" -o -name "client*.txt" \) -type f | head -20); do
    echo "##### $f" >> "$OUT/members.txt"
    while read -r m; do
      [ -z "$m" ] && continue
      grep -n -m 5 -w -- "$m" "$f" >> "$OUT/members.txt" 2>/dev/null
    done < ci/members.txt
  done
  for z in $(find "$FG" -name "mcp_config-*.zip" | head -2); do
    echo "##### $z" >> "$OUT/members.txt"
    unzip -l "$z" >> "$OUT/members.txt"
  done
fi
exit 0
