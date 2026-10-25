# Liest javap -c -p Ausgabe (stdin) und zeigt, wo die Xaero-Tracker-Manager geholt und benutzt werden.
import sys, re
cls = None; meth = None
keys = ("RenderedPlayerTrackerManager", "PlayerTrackerSystemManager")
shown = set()
for line in sys.stdin:
    line = line.rstrip("\n")
    m = re.match(r'^(public |final |abstract |)*(class|interface) (\S+)', line.replace("public final ", "public "))
    if line.startswith("Compiled from"):
        cls = None; continue
    if re.match(r'^[a-z].*(class|interface) ', line):
        cls = line.split("{")[0]; continue
    if re.match(r'^  [a-z].*\);$', line) or re.match(r'^  [a-z].*;$', line) and "(" in line:
        meth = line.strip()
        if any(k in meth for k in keys) and (cls, meth) not in shown:
            shown.add((cls, meth)); print("DECL", cls, "::", meth)
        continue
    if any(k in line for k in keys) and ("invoke" in line or "getstatic" in line or "getfield" in line):
        key = (cls, meth, line.split("//")[-1].strip())
        if key not in shown:
            shown.add(key); print("USE ", cls, "::", meth, "->", line.split("//")[-1].strip())
