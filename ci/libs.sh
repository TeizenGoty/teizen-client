#!/bin/sh
# Lädt Litematica/malilib für eine Minecraft-Version nach libs/, falls sie fehlen (Werte aus versions/<mc>.properties).
MCV="$1"
P="versions/$MCV.properties"
get() { grep "^$1=" "$P" | head -1 | cut -d= -f2-; }
for k in litematica malilib; do
  J=$(get ${k}_jar); U=$(get ${k}_url)
  if [ -n "$J" ] && [ ! -s "libs/$J" ] && [ -n "$U" ]; then curl -sL "$U" -o "libs/$J"; echo "geladen: libs/$J"; fi
done
