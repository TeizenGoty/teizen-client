#!/usr/bin/env python3
"""Erstellt Teizen-Keys, die an einen Spielernamen gebunden sind (Ed25519-Signatur).
Benutzung: python3 ci/make-key.py --key <teizen-signing-key.pem> <Name> [<Name> ...] [--out <Ordner>]
Der geheime Schlüssel (teizen-signing-key.pem) liegt NICHT im Repo – der Besitzer hebt ihn selbst auf."""
import base64, json, os, sys, zipfile
from cryptography.hazmat.primitives import serialization

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def main():
    args = sys.argv[1:]
    out = os.path.join(ROOT, "out-keys")
    keyfile = None
    if "--out" in args:
        i = args.index("--out"); out = args[i + 1]; del args[i:i + 2]
    if "--key" in args:
        i = args.index("--key"); keyfile = args[i + 1]; del args[i:i + 2]
    if not args or not keyfile:
        print(__doc__); sys.exit(1)
    with open(keyfile, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    os.makedirs(out, exist_ok=True)
    for name in args:
        if not all(c.isalnum() or c == "_" for c in name) or not 1 <= len(name) <= 16:
            print("Ungültiger Minecraft-Name:", name); continue
        msg = ("teizen-key-v2|" + name.lower()).encode()
        sig = base64.b64encode(key.sign(msg)).decode()
        meta = {
            "schemaVersion": 1, "id": "teizenkey", "version": "2.0.0", "name": "Teizen Key (" + name + ")",
            "description": "Key für die Teizen-Client-Version mit Key-Pflicht. Geht nur mit dem Spielernamen " + name + ".",
            "authors": ["Teizen"], "license": "All-Rights-Reserved", "environment": "*",
            "depends": {"fabricloader": ">=0.19.0"},
        }
        path = os.path.join(out, "teizen-key-" + name + ".jar")
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("fabric.mod.json", json.dumps(meta, indent=2, ensure_ascii=False))
            z.writestr("teizen-key.txt", "v2\nname=" + name + "\nsig=" + sig + "\n")
        print("Key:", path)

if __name__ == "__main__":
    main()
