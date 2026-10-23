#!/usr/bin/env bash
# Reads a Google TV or Android TV over adb and writes a plain-text report to reports/: what's installed and turned off,
# what runs and how much memory it takes, the home screen, storage, and Lumen's own numbers. Read-only: it
# changes nothing on the TV. Run it before a change and after, then compare the two files.
#   tools/tv-report.sh <TV IP> [label]        e.g.  tools/tv-report.sh 192.168.1.50 before
# Left out on purpose: Wi-Fi networks, accounts, serial numbers and logs. The report stays on this computer (reports/
# is not committed).
set -u
export MSYS_NO_PATHCONV=1   # Git Bash would turn /data and /proc/... into Windows paths
cd "$(dirname "$0")/.." || exit 1
# shellcheck source=/dev/null
if [ -f .tools/env.sh ]; then . ./.tools/env.sh; fi   # adb from tools/setup.sh

[ $# -ge 1 ] || { echo "Usage: tools/tv-report.sh <TV IP> [label]"; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb not found: install Android's platform-tools (see README)."; exit 1; }
T="$1"; case "$T" in *:*) ;; *) T="$T:5555" ;; esac
LABEL="$(printf '%s' "${2:-}" | tr -c 'A-Za-z0-9._-' '_')"

# --- Connect (the TV asks to allow debugging the first time)
state=""; asked=0; i=0
while [ $i -lt 40 ]; do
  adb connect "$T" >/dev/null 2>&1
  state="$(adb -s "$T" get-state 2>/dev/null | tr -d '\r')"
  [ "$state" = "device" ] && break
  if [ $asked = 0 ]; then echo "On the TV: accept \"Allow USB debugging?\" (tick \"Always allow\")."; asked=1; fi
  sleep 2; i=$((i + 1))
done
if [ "$state" != "device" ]; then
  echo "Couldn't reach the TV at $T. Check that USB debugging is on and that this computer is on the TV's Wi-Fi."
  echo "On a Mac, \"No route to host\" while the TV answers: see Local Network in CLAUDE.md."
  exit 1
fi

tv() { adb -s "$T" shell "$1" 2>&1 | tr -d '\r'; }
step() { printf '%s\n' "$1" >&2; }

BODY="$(mktemp)"; trap 'rm -f "$BODY"' EXIT
section() { printf '\n== %s ==\n' "$1" >> "$BODY"; }
add() { cat >> "$BODY"; }

# --- Device
step "Reading the TV…"
PROPS="ro.product.brand ro.product.manufacturer ro.product.model ro.product.device ro.product.name
  ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.build.fingerprint
  ro.board.platform ro.hardware ro.product.cpu.abilist ro.config.low_ram ro.surface_flinger.max_graphics_width
  ro.surface_flinger.max_graphics_height ro.sf.lcd_density dalvik.vm.heapsize dalvik.vm.heapgrowthlimit"
DEVICE="$(tv "for p in $(echo $PROPS); do echo \"\$p=\$(getprop \$p)\"; done")"
val() { printf '%s\n' "$DEVICE" | grep -m1 "^$1=" | cut -d= -f2-; }
ALLPROPS="$(tv getprop)"
section "Device"
printf '%s\n' "$DEVICE" | add
printf '%s\n' "$ALLPROPS" | grep -E '^\[ro\.lmk\.' | add
echo "Brand properties (names only):" | add
printf '%s\n' "$ALLPROPS" | grep -iE 'mitv|xiaomi|miui' | cut -d']' -f1 | tr -d '[' | sed 's/^/  /' | add

section "Display"
tv "wm size; wm density" | add

# --- Memory and CPU
step "Reading memory (takes a few seconds)…"
MEM="$(tv 'cat /proc/meminfo')"
MEMINFO="$(tv 'dumpsys meminfo')"
section "Memory"
printf '%s\n' "$MEM" | grep -E '^(MemTotal|MemFree|MemAvailable|Cached|SwapTotal|SwapFree):' | add
printf '%s\n' "$MEMINFO" | grep -E '^ *(Total RAM|Free RAM|Used RAM|Lost RAM|ZRAM|Tuning):' | add
section "Memory by process (PSS, top 40)"
printf '%s\n' "$MEMINFO" | awk '/^Total PSS by process:/ {f = 1; next} f && /^$/ {exit} f' | head -40 | add
section "Memory by importance (OOM adjustment)"
printf '%s\n' "$MEMINFO" | awk '/^Total PSS by OOM adjustment:/ {f = 1; next} f && /^$/ {exit} f' | head -80 | add
section "CPU (top)"
tv 'top -b -n 1 -m 20 2>/dev/null || top -n 1 -m 20' | add
section "CPU since boot (cpuinfo)"
tv 'dumpsys cpuinfo' | head -25 | add
section "Running services, by package"
tv 'dumpsys activity services' | grep -oE 'ServiceRecord\{[^}]+\}' | sort -u | awk '{print $3}' | cut -d/ -f1 \
  | sort | uniq -c | sort -rn | head -40 | add

# --- Storage
section "Storage"
DF="$(tv 'df -h /data')"
printf '%s\n' "$DF" | add
tv 'dumpsys diskstats' | grep -E '^(Data-Free|System-Free|Cache-Free|App Size|App Data Size|App Cache Size):' | add

# --- Home screen, screensaver, inputs, keyboard
step "Reading the home screen and apps…"
HOMES="$(tv 'cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME')"
HOLDER="$(tv 'cmd role get-role-holders --user 0 android.app.role.HOME')"
# Android 9 has no `cmd role`: then ask which activity answers the Home button (as HomeMode.homeHolder does).
if ! printf '%s' "$HOLDER" | grep -qE '^[A-Za-z0-9_.]+(;[A-Za-z0-9_.]+)*$'; then
  HOLDER="$(tv 'cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -1)"
fi
section "Home screen"
echo "Home button opens: $HOLDER" | add
echo "Home screens on the TV:" | add
printf '%s\n' "$HOMES" | grep / | sed 's/^ */  /' | add
tv 'for k in screensaver_enabled screensaver_components screensaver_default_component; do echo "$k=$(settings get secure $k)"; done' | add
section "TV apps (LEANBACK_LAUNCHER)"
tv 'cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER' \
  | grep / | sed 's/^ */  /' | sort | add
section "Live TV and inputs"
echo "TV input services:" | add
tv 'cmd package query-services --brief -a android.media.tv.TvInputService' | grep / | sed 's/^ */  /' | sort | add
echo "Opens a channel (the live TV app):" | add
tv 'cmd package query-activities --brief -a android.intent.action.VIEW -d content://android.media.tv/channel' \
  | grep / | sed 's/^ */  /' | add
section "Keyboard, accessibility, voice"
tv 'for k in default_input_method enabled_input_methods enabled_accessibility_services tts_default_synth voice_interaction_service; do echo "$k=$(settings get secure $k)"; done' | add
section "Animations (null = the TV's default, 1)"
ANIM="$(tv 'for k in window_animation_scale transition_animation_scale animator_duration_scale; do echo "$k=$(settings get global $k)"; done')"
printf '%s\n' "$ANIM" | add

# --- Lumen
section "Lumen"
tv 'dumpsys package io.lumen.tv' | grep -m3 -E 'versionName=|lastUpdateTime=|firstInstallTime=' | sed 's/^ */  /' | add
tv 'for k in lumen_disabled lumen_animations; do echo "$k=$(settings get global $k)"; done' | add
echo "Frames drawn by the home screen and its settings (since the process started):" | add
GFX="$(tv 'dumpsys gfxinfo io.lumen.tv:home' | grep -E 'Stats since|Total frames|Janky frames|percentile|Number ')"
printf '%s\n' "${GFX:-  (not running now)}" | sed 's/^ */  /' | add

# --- Packages
SYS="$(tv 'pm list packages -s' | sed -n 's/^package://p' | sort)"
USER_APPS="$(tv 'pm list packages -3' | sed -n 's/^package://p' | sort)"
OFF="$(tv 'pm list packages -d' | sed -n 's/^package://p' | sort)"
count() { printf '%s\n' "$1" | grep -c .; }

step "Reading how apps are compiled…"
SHELF="io.lumen.tv com.netflix.ninja com.google.android.youtube.tv com.amazon.amazonvideo.livingroom
  com.disney.disneyplus com.wbd.stream com.hbo.hbonow com.apple.atve.androidtv.appletv com.hulu.livingroomplus
  com.cbs.ott com.peacocktv.peacockandroid com.globo.globotv com.spotify.tv.android"
DEXOPT="$(tv 'dumpsys package dexopt' | awk '/^  \[/ {pkg = $1; gsub(/[][]/, "", pkg); next}
  /status=/ {s = $0; sub(/^ +/, "", s); print pkg ": " s}')"
section "How apps are compiled (all packages, by status)"
printf '%s\n' "$DEXOPT" | sed -n 's/.*status=\([^]]*\)].*/\1/p' | sort | uniq -c | sort -rn | add
section "How apps are compiled (Lumen, shelf apps, installed apps)"
for p in $(echo $SHELF) $USER_APPS; do printf '%s\n' "$DEXOPT" | grep "^$p: "; done | awk '!seen[$0]++' | add

step "Reading the TV maker's system apps…"
MAKER="$(printf '%s\n' "$SYS" | grep -E '^(com\.xiaomi|com\.xiaomo|com\.mitv|com\.mi\.|com\.miui|mitv\.|com\.xm\.|android\.autoinstalls|com\.droidlogic|com\.amlogic|com\.mstar|com\.mediatek|com\.realtek|com\.rtk|com\.cvte|com\.google\.android\.(tvlauncher|tvrecommendations|leanbacklauncher|backdrop|katniss))')"
section "TV maker and chip system apps (uid, flags, where, state)"
# 20 packages per adb call: older TVs cap the length of one shell command.
printf '%s\n' "$MAKER" | grep . | paste -d' ' - - - - - - - - - - - - - - - - - - - - | while read -r batch; do
  tv "for p in $batch; do echo \"[\$p]\"; dumpsys package \$p | grep -m5 -E 'userId=|codePath=|versionName=|pkgFlags=|User 0:' | sed 's/^ */  /'; done"
done | add
section "System apps (pm list packages -s)"
printf '%s\n' "$SYS" | add
section "Installed apps (pm list packages -3)"
printf '%s\n' "$USER_APPS" | add
section "Turned off (pm list packages -d)"
printf '%s\n' "${OFF:-(none)}" | add

# --- Summary on top, then everything else
model="$(val ro.product.model | tr -d '\n' | tr -c 'A-Za-z0-9._-' '_')"
mkdir -p reports
OUT="reports/${model:-tv}-$(date +%Y%m%d-%H%M)${LABEL:+-$LABEL}.txt"
mb() { printf '%s\n' "$MEM" | awk -v k="$1:" '$1 == k {printf "%d MB", $2 / 1024}'; }
{
  echo "Lumen: TV report, $(date '+%Y-%m-%d %H:%M')${LABEL:+ ($LABEL)}"
  echo
  echo "== Summary =="
  echo "TV:          $(val ro.product.brand) $(val ro.product.model) ($(val ro.product.device)), Android $(val ro.build.version.release) (SDK $(val ro.build.version.sdk))"
  echo "Chip:        $(val ro.board.platform) / $(val ro.hardware), low_ram=$(val ro.config.low_ram)"
  echo "Memory:      $(mb MemTotal) total, $(mb MemAvailable) available"
  echo "Storage:     $(printf '%s\n' "$DF" | tail -1 | awk '{print $4 " free of " $2 " (" $5 " used)"}')"
  echo "Packages:    $(count "$SYS") system, $(count "$USER_APPS") installed, $(count "$OFF") turned off"
  echo "Home button: $HOLDER"
  echo "Animations:  $(printf '%s\n' "$ANIM" | cut -d= -f2 | tr '\n' ' ')(window, transition, animator)"
  echo "Home frames: $(printf '%s\n' "$GFX" | grep -m1 'Janky frames' | sed 's/^ *//')"
  cat "$BODY"
} > "$OUT"
echo "Saved $OUT"
