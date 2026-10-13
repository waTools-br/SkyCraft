#!/usr/bin/env python3
"""Boots a real Fabric 26.3 dedicated server with the built SkyCraft jar and checks it runs.

Usage: python tools/smoke_server.py <skycraft jar> [work dir]

What it proves without Skyrim: the mod loads, every mixin whose target class loads at startup or
while the commands below run applies (a broken mixin crashes the server), the worlds data pack
loads (Tamriel Wilds, Home, the Nether), and structures are found in a world made like SkyCraft's
(the mirror preset, seed 0, structures off). The server runs the mod's common half, as the client's
integrated server does. Runs on Windows only: SkyLink binds kernel32 when it
loads. The server's log is printed at the end; the exit code is 1 when a check fails.
"""
import json
import os
import socket
import struct
import subprocess
import sys
import time
import urllib.request
import zipfile

MINECRAFT = "26.3"
LOADER = "0.19.5"
FABRIC_API = "0.161.0+26.3"
RCON_PORT = 25575
RCON_PASSWORD = "skycraft-smoke"

# (command, text the answer must contain, or None to only print it)
CHECKS = [
    ("execute in skycraft:tamriel_wilds run locate structure skycraft:dwemer_tower", "nearest"),
    ("execute in skycraft:tamriel_wilds run locate structure #minecraft:village", "nearest"),
    ("execute in skycraft:tamriel_wilds run locate structure minecraft:pillager_outpost", "nearest"),
    ("execute in skycraft:tamriel_wilds run locate biome skycraft:the_rift", "nearest"),
    ("execute in minecraft:the_nether run locate structure minecraft:fortress", "nearest"),
    ("execute in skycraft:home run locate biome skycraft:home_fields", "nearest"),
    ("gamerule minecraft:spawn_mobs", None),
    ("gamerule minecraft:spawn_monsters", None),
    # A few ticks in the other worlds with chunks loaded and mobs in them (spawning and mob AI under the mixins).
    ("execute in skycraft:tamriel_wilds run forceload add 96 288", None),
    ("execute in skycraft:tamriel_wilds positioned 96 200 288 run summon minecraft:zombie ~ ~ ~", "Summoned"),
    ("execute in minecraft:the_nether run forceload add 0 0", None),
    ("execute in minecraft:the_nether positioned 0 80 0 run summon minecraft:piglin ~ ~ ~", "Summoned"),
    ("summon minecraft:zombie 0 100 0", "Summoned"),
]
BAD_LOG = ("Mixin apply failed", "InvalidInjectionException", "InvalidMixinException", "MixinApplyError", "Exception in server tick loop",
           "This crash report", "Encountered an unexpected exception", "Caused by:")


def fetch(url):
    """The body at url; three tries, two minutes each (Fabric's servers sometimes hang or answer 502)."""
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=120) as response:
                return response.read()
        except OSError as e:
            if attempt == 2:
                raise
            print(f"retrying {url}: {e}")
            time.sleep(15)


def download(url, path):
    if not os.path.exists(path):
        print(f"downloading {url}")
        data = fetch(url)
        with open(path, "wb") as f:
            f.write(data)


def rcon_packet(sock, req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)
    size = struct.unpack("<i", recv_exact(sock, 4))[0]
    rid, _ = struct.unpack("<ii", recv_exact(sock, 8))
    return rid, recv_exact(sock, size - 8)[:-2].decode("utf-8", "replace")


def recv_exact(sock, n):
    out = b""
    while len(out) < n:
        chunk = sock.recv(n - len(out))
        if not chunk:
            raise ConnectionError("rcon closed")
        out += chunk
    return out


def main():
    jar = os.path.abspath(sys.argv[1])
    work = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else "smoke")
    os.makedirs(os.path.join(work, "mods"), exist_ok=True)
    installers = json.loads(fetch("https://meta.fabricmc.net/v2/versions/installer"))
    installer = next(i["version"] for i in installers if i.get("stable"))
    launcher = os.path.join(work, "fabric-server-launch.jar")
    download(f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}/{LOADER}/{installer}/server/jar", launcher)
    download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{FABRIC_API}/fabric-api-{FABRIC_API.replace('+', '%2B')}.jar",
             os.path.join(work, "mods", "fabric-api.jar"))
    # The mod says it's client only (a dedicated server would skip it); a copy that says "*" runs its common half here,
    # which is what the integrated server inside the client runs.
    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(os.path.join(work, "mods", "skycraft.jar"), "w", zipfile.ZIP_DEFLATED) as dst:
        for entry in src.infolist():
            data = src.read(entry.filename)
            if entry.filename == "fabric.mod.json":
                data = data.replace(b'"environment": "client"', b'"environment": "*"', 1)
            dst.writestr(entry, data)
    with open(os.path.join(work, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    # Made like the SkyCraft world (MirrorWorld): the mirror preset, seed 0, structures off.
    with open(os.path.join(work, "server.properties"), "w") as f:
        f.write("\n".join([
            "level-type=skycraft\\:mirror", "level-seed=0", "generate-structures=false", "online-mode=false",
            "enable-rcon=true", f"rcon.port={RCON_PORT}", f"rcon.password={RCON_PASSWORD}", "spawn-protection=0",
            "max-tick-time=-1", "view-distance=4", "simulation-distance=4", "",
        ]))

    log_path = os.path.join(work, "server.log")
    log = open(log_path, "w", encoding="utf-8", errors="replace")
    server = subprocess.Popen(["java", "-Xmx3G", "-jar", launcher, "nogui"], cwd=work, stdout=log, stderr=subprocess.STDOUT,
                              stdin=subprocess.DEVNULL)
    failures = []
    try:
        deadline = time.time() + 900
        while True:
            time.sleep(3)
            text = open(log_path, encoding="utf-8", errors="replace").read()
            if "Done (" in text and "RCON running" in text:
                break
            if server.poll() is not None:
                failures.append(f"the server stopped while starting (exit {server.returncode})")
                break
            if time.time() > deadline:
                failures.append("the server didn't start within 15 minutes")
                break
        if not failures:
            with socket.create_connection(("127.0.0.1", RCON_PORT), timeout=120) as sock:
                rid, _ = rcon_packet(sock, 1, 3, RCON_PASSWORD)
                if rid == -1:
                    raise RuntimeError("rcon login refused")
                for n, (command, expect) in enumerate(CHECKS, start=2):
                    _, answer = rcon_packet(sock, n, 2, command)
                    ok = expect is None or expect.lower() in answer.lower()
                    print(f"{'ok  ' if ok else 'FAIL'} /{command}\n     -> {answer.strip()}")
                    if not ok:
                        failures.append(f"/{command} answered: {answer.strip()}")
                time.sleep(20)  # let the worlds tick with the summoned mobs
                rcon_packet(sock, 99, 2, "save-all flush")
                try:
                    rcon_packet(sock, 100, 2, "stop")
                except (ConnectionError, OSError):
                    pass
        server.wait(timeout=180)
    except Exception as e:  # noqa: BLE001 - report anything, then the log
        failures.append(f"{type(e).__name__}: {e}")
    finally:
        if server.poll() is None:
            server.kill()
        log.close()

    text = open(log_path, encoding="utf-8", errors="replace").read()
    for bad in BAD_LOG:
        if bad in text:
            failures.append(f"the log has '{bad}'")
    print("\n===== server log =====")
    print(text[-60000:])
    print("===== end of server log =====")
    if failures:
        print("\nSMOKE TEST FAILED:\n  " + "\n  ".join(failures))
        sys.exit(1)
    print("\nsmoke test passed")


if __name__ == "__main__":
    main()
