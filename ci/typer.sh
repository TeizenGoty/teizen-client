#!/bin/bash
# Tippt wie eine echte Tastatur (X11 über xdotool) ins Spielfenster, sobald der Selbsttest das anfordert:
# Spiel schreibt den Text nach run/teizen-type.txt, danach meldet dieses Skript run/teizen-typed.txt.
# So wird der echte Weg Tastatur -> SDL/GLFW -> Minecraft -> Menü getestet (nicht nur charTyped direkt).
REQ=run/teizen-type.txt
DONE=run/teizen-typed.txt
LOG=typer-log.txt
echo "typer gestartet (DISPLAY=$DISPLAY)" >> "$LOG"
while true; do
  if [ -f "$REQ" ]; then
    TXT=$(cat "$REQ"); rm -f "$REQ"
    # Fenster suchen (Name, sonst Klasse); 26.3 (SDL) heißt evtl. anders -> alle Fenster mitloggen
    WID=$(xdotool search --name "Minecraft" 2>>"$LOG" | tail -1)
    [ -z "$WID" ] && WID=$(xdotool search --class "minecraft|java|Minecraft" 2>>"$LOG" | tail -1)
    echo "$(date +%T) tippe '$TXT' in Fenster '$WID'" >> "$LOG"
    for w in $(xdotool search --name "." 2>/dev/null | head -10); do echo "  Fenster $w: $(xdotool getwindowname $w 2>/dev/null)" >> "$LOG"; done
    if [ -n "$WID" ]; then
      xdotool windowfocus --sync "$WID" >> "$LOG" 2>&1
      eval "$(xdotool getwindowgeometry --shell "$WID")"
      xdotool mousemove --window "$WID" $((WIDTH / 2)) $((HEIGHT - 5)) >> "$LOG" 2>&1
    else
      # kein Fenstermanager: Tasten gehen an das Fenster unter der Maus -> Maus in die Bildschirmmitte
      xdotool mousemove 640 715 >> "$LOG" 2>&1
    fi
    sleep 0.3
    xdotool type --delay 80 "$TXT" >> "$LOG" 2>&1
    echo "$(date +%T) fertig (exit $?)" >> "$LOG"
    sleep 0.5
    touch "$DONE"
  fi
  sleep 0.2
done
