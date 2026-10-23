# Install Lumen on my TV: instructions for my AI agent

I own the TV. Please install **Lumen**, my build of a fast home screen for Android TV and Google TV, on my Xiaomi TV.
It replaces the **Free My TV** app that is on the TV now. Do as much as you can yourself. I'll only do what needs a
person at the TV. Read this whole file before you start. It takes 20 to 40 minutes; the build and the switch are the
slow parts.

## What I'll tell you (if I didn't, ask me once)

- **The TV's IP address.** If I don't know it, find it (step 5).
- **My city**, for the weather and the evening dark mode. Work out its latitude, longitude, country and currency.

My choices, unless my message says otherwise:

- **Google Assistant: off.** I don't use "Ok Google"; the remote's mic button may stop working, and that's fine.
- **TV animations: faster.**
- **Lighter home screen: on.** I care more about speed than animations.
- **TMDB key** (it powers the movie and show picks): reuse the one from my old Free My TV setup if it's on this
  computer. If not, I'll paste one into the file myself when you ask, or we skip it.
- **Apps to turn off:** show me your list and wait for my OK.

## What I'll do (only this)

1. Keep the TV on and on the same Wi-Fi as this computer, and stay near it with the remote during the TV part.
2. Accept **"Allow USB debugging?"** on the TV when you tell me: tick *Always allow*, then press OK. It can come up to
   three times: for this computer (if it's new to the TV), for the old app, and for Lumen itself.
3. Answer when you ask me something, like which apps to turn off.
4. If the Mac asks whether an app (your terminal, or the app you run in) may find devices on the local network, allow
   it. adb can't reach the TV without that.

## Rules

1. **Use only** `https://github.com/niveshsaharan/free-my-tv`. Never use anything from the original Free My TV project
   (`simbasjungles/free-my-tv`): not its code, its releases or its website.
2. **Keys are secret.** Never print, paste, log or commit `tmdb_key` or `watchmode_key`, and never ask me to type them
   in the chat. Check whether a key is set without showing it (step 3).
3. **Follow the repo's `CLAUDE.md` (or `AGENTS.md`), with two changes I'm asking for:**
   - You press the TV's buttons. Use `adb shell input keyevent …` to go through the apps' own menus, and take a
     screenshot after each press (see "Handy commands"). This overrides CLAUDE.md's "let them do the taps".
   - CLAUDE.md's "don't switch modes from the computer" still holds. Modes change only through the app's own
     *Home Screen* menu. Never do it by hand with `pm disable-user`, `pm uninstall`, `cmd package set-home-activity`
     or `settings put`. The one exception is "Recovery" at the end, and only after I say yes.
4. **Never accept "Allow USB debugging?" yourself.** Tell me one is coming and wait. Send no key presses while it's on
   screen.
5. **Don't touch the TV's own apps or settings.** Never uninstall or clear the data of the TV's own apps, and never
   factory reset. Don't change picture, sound, network or account settings.
6. **Tell me first.** Before anything that changes the TV, tell me in one line what's about to happen. Then do it.
7. **Stop when something looks different.** If the screen or an output doesn't match what these steps say, stop and
   tell me what you see. Don't improvise on the TV.

## Handy commands

Run them in the repo folder (step 1). If the tools came from `tools/setup.sh` (step 2), start each command with
`. .tools/env.sh && ` so `adb` and `java` are found.

Keep only the TV connected: `adb devices` should list just it, so plain `adb` commands go to the TV. If anything else
is listed, put `-s <IP>:5555` after `adb` in every command.

- **Screenshot** (look at it after every step that changes the screen):
  `adb shell screencap -p /data/local/tmp/tv.png && adb pull /data/local/tmp/tv.png /tmp/tv.png`
- **Press a remote button:** `adb shell input keyevent KEYCODE_DPAD_CENTER` (OK). The others are `KEYCODE_DPAD_UP`,
  `KEYCODE_DPAD_DOWN`, `KEYCODE_BACK` and `KEYCODE_HOME`. Wait about a second between presses.
- **What the Home button opens now** (the "Home check"):
  `adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1`

## 1. The code

- **Reading this file from a clone** of `https://github.com/niveshsaharan/free-my-tv`? Run `git pull` in it and work
  there.
- **Otherwise, with git:** `git clone https://github.com/niveshsaharan/free-my-tv ~/lumen`, then work in `~/lumen`.
  On a Mac, use git only if `xcode-select -p` answers with a folder. Without Apple's developer tools, `git` (and
  `python3`) open an installer window instead of running.
- **Without git:** download the code with `curl`, which every Mac has:
  `rm -rf /tmp/free-my-tv-main && curl -fL -o /tmp/lumen.zip https://codeload.github.com/niveshsaharan/free-my-tv/zip/refs/heads/main && unzip -qo /tmp/lumen.zip -d /tmp && mv /tmp/free-my-tv-main ~/lumen`,
  then work in `~/lumen`.

From here on, "the repo folder" is that folder. If `./build.sh` or a script in `tools/` says "permission denied",
run it with `bash` in front.

## 2. Tools on this computer

The build needs a JDK (17 or newer) and Android's build tools, and you need `adb` to reach the TV. This Mac doesn't
have to have any of them.

1. **If they're already installed,** skip this step: `java -version` says 17 or newer, `adb version` answers, and the
   Android SDK (`$ANDROID_HOME`, `~/Library/Android/sdk` or `~/Android/Sdk`) has `build-tools` 34 or newer and
   `platforms/android-34`.
2. **Otherwise, run** `tools/setup.sh --accept-android-sdk-license` in the repo folder. I accept the Android SDK
   License Agreement, so you may pass that flag.
   - It downloads Amazon's JDK 17 (Corretto), Android's build-tools and platform 34, and adb (about 350 MB) into
     `.tools/` in the repo folder.
   - It checks each download against the checksum Amazon or Google publishes.
   - It needs no Homebrew and no password, and installs nothing on the Mac.
   - It ends with `Ready`.
3. **Using them:** `./build.sh` and `tools/tv-report.sh` find these tools by themselves. For your own `adb` and `java`
   commands, start each command with `. .tools/env.sh && `.

On Windows, install a JDK 17, Android's command-line tools and adb as the README says (Git Bash or WSL).

## 3. Settings (`config.properties`)

1. **Look for my old Free My TV folder on this computer.** It holds `freemytv.keystore`, or a `config.properties` whose
   first line is `# Free My TV settings`. To find it:
   `find ~ -maxdepth 4 \( -name freemytv.keystore -o -name config.properties \) 2>/dev/null`.
   If it's there, copy its `config.properties` into the repo folder with `cp`, without opening or printing it.
2. **No old folder?** Run `./build.sh` once. It creates `config.properties` from the example.
3. **Make sure these lines are right**, and edit only these:
   - `latitude`, `longitude` and `city`: my city.
   - `tmdb_region`: my country, in two letters.
   - `currency`: mine. If Binance has no pairs in it, like INR, prices show in dollars, so use `USD`.
   - `disable_assistant`: `true` (my choice above).
   - Leave `temperature=auto` unless I asked for °C or °F.
4. **Show me the settings without the keys:**
   `grep -v -E '^[[:space:]]*(#|$|tmdb_key|watchmode_key)' config.properties`
5. **Check the key without showing it:**
   `grep -q -E '^[[:space:]]*tmdb_key[[:space:]]*=[[:space:]]*[^[:space:]]' config.properties && echo "TMDB key: set" || echo "TMDB key: empty"`
   - If it's empty, tell me. I can make a free key (themoviedb.org → Settings → API) and paste it after `tmdb_key=` in
     the repo folder's `config.properties` myself.
   - Don't wait for it: without a key everything works except the picks and the screensaver photos.

## 4. Build

1. Run `./build.sh`. It must end with `Built Lumen.apk`.
   - If `d8` fails with a NullPointerException (a new JDK with build-tools 34), install `build-tools;35.0.1`.
     `build.sh` uses the newest it finds.
2. Back up the signing key and the settings. Every update needs this same key:
   `mkdir -p ~/Lumen-backup && cp lumen.keystore config.properties ~/Lumen-backup/`
3. Never commit them. `.gitignore` already keeps them out.

## 5. Connect to the TV

Tell me: "Ready for the TV part: please stay by the TV with the remote for about 15 minutes." Then:

1. **If you don't have the IP,** check `adb devices`: it may already list the TV. If not, look for it on this network
   with `java tools/FindTv.java`, from the repo folder. It lists the devices that answer on port 5555 and needs only
   Java. More than one found? Connect to each, run `adb shell getprop ro.product.manufacturer`, and keep the Xiaomi.
   Nothing found: ask me for the IP (Settings → Network & Internet on the TV), and check that the Mac allowed local
   network access.
2. **Connect:** `adb connect <IP>:5555`. Then `adb devices` must show `<IP>:5555  device`.
   - **`unauthorized`:** the TV is asking "Allow USB debugging?". Tell me, and wait.
   - **Connection refused, or no answer:** debugging is off. Ask me to turn it on: Settings → Device Preferences (or
     System) → About → press *Build* 7 times, then Developer options → *USB debugging* on (and *Network debugging*,
     if the TV has it).
   - **On a Mac, "No route to host" while the TV answers:** run `adb kill-server; adb nodaemon server &` in the same
     terminal, or allow the terminal app under System Settings → Privacy & Security → Local Network.
3. **Check it's my TV:** `adb shell getprop ro.product.manufacturer`, `ro.product.model` and
   `ro.build.version.release`.

## 6. A report before changing anything (read-only)

Run `tools/tv-report.sh <IP> before`. It writes `reports/<model>-<date>-before.txt`, without Wi-Fi networks, accounts
or serial numbers. Keep it: I'll share it with Claude later to tune Lumen for Xiaomi TVs.

## 7. Turn the old Free My TV back to Original, then remove it

The old app is the package `io.freemytv`. It shows as *Free My TV*, or as *Lumen* if it was a test build. Removing it
while it's on would leave the TV without its own home screen, so it goes back to Original first.

1. **Is it installed?** Run `adb shell pm list packages io.freemytv`.
   - If nothing is listed, check `adb shell settings get global free_my_tv_disabled`. If that lists packages, the old
     app was removed while it was on: see "Recovery" and ask me first. Otherwise go to step 8.
2. **Is it on?** It is if the Home check shows `io.freemytv/…`, or if `free_my_tv_disabled` lists packages. If it's
   already on Original, go to 7.7.
3. **Tell me:** "I'm switching the old app back to Original. The TV's own home screen, screensaver and apps come
   back. It takes a minute or two, and the TV may jump between screens while it measures. Don't press anything unless
   it asks Allow USB debugging?."
4. **Open its settings fresh:** `adb shell am start -W --activity-clear-task -n io.freemytv/.SettingsActivity`.
   Wait 3 s and take a screenshot. You should see its settings, with the first row, *Home Screen*, focused (white).
5. **Pick Original:**
   1. Press OK. A screen with two choices opens: the app's own mode first, then *Original*.
   2. Press DOWN and take a screenshot: *Original* should be focused.
   3. Press OK. You're back on the list, and the *Home Screen* row shows its steps with a spinner.
6. **Wait for it.** Every 10 s, for up to 5 minutes, run the Home check and
   `adb shell settings get global free_my_tv_disabled`. It's done when:
   - the Home check no longer shows `io.freemytv`;
   - `free_my_tv_disabled` shows `null`. Anything still listed didn't come back: tell me, and see "Recovery".

   Then wait one more minute, since it measures the home screen at the end. Then check:
   - `adb shell settings get secure screensaver_components` no longer mentions `io.freemytv`.
   - Press Home and take a screenshot: the TV's own home screen.
7. **Remove it.** Tell me, then run `adb uninstall io.freemytv`. It should answer `Success`.

## 8. Install Lumen

Run `adb install -r Lumen.apk`. It should answer `Success`.

If Lumen is already on the TV from another computer and this fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`:

1. Switch that one to *Original* in its own menu, as in step 7.
2. `adb uninstall io.lumen.tv`.
3. Install again.

## 9. Switch the TV to Lumen

1. **Tell me what it does:** the home screen and screensaver become Lumen's, and a short list of promo and telemetry
   apps is turned off (never uninstalled). *Lumen* → *Home Screen* → *Original* brings it all back.
   Also tell me: "In a moment the TV asks Allow USB debugging? for Lumen itself: tick Always allow and press OK."
   Wait until I say I'm at the TV.
2. **Open Lumen's settings:** `adb shell am start -W --activity-clear-task -n io.lumen.tv/.SettingsActivity`.
   Wait 3 s and take a screenshot: Lumen's settings, with *Home Screen* focused.
3. **Pick Lumen:**
   1. Press OK. Two choices: *Lumen* and *Original*, with *Original* focused.
   2. Press UP and take a screenshot: *Lumen* should be focused.
   3. Press OK.
4. **The prompt.** When "Allow USB debugging?" shows on a screenshot, stop pressing keys and wait for me to accept it.
5. **Wait for it.** Every 10 s, for up to 5 minutes: it's done when the Home check shows `io.lumen.tv/…` and nothing
   has moved on screen for a minute. The TV jumps between home screens while Lumen measures them.
6. **If it stops with a message** (amber text under the list), read it on a screenshot and see "If it stops".
7. **Check:**
   - `adb logcat -d -s Lumen | tail -n 60`.
   - `adb shell settings get global lumen_disabled`: what went off.
   - `adb shell settings get secure screensaver_components`: should mention `io.lumen.tv`.
   - Press Home and take a screenshot: Lumen's home screen.

## 10. Make it faster (the Speed screen)

1. **Open Speed.** Open Lumen's settings as in 9.2. Its list is: Home Screen, Screensaver, Home Settings, Shelf, Top
   Buttons, Remote Buttons, **Speed**, TV Settings. From the top, press DOWN 6 times, then OK.
2. **How it works.** Speed's rows are *Turn off apps*, *Google Assistant*, *Animations* and *Lighter home screen*.
   Pressing OK on a row moves it to its next value. Take a screenshot after each press.
3. **Set the rows:**
   - *Animations:* press DOWN 2 times, then OK until it says *Faster*. A few seconds later,
     `adb shell settings get global window_animation_scale` should answer `0.5`.
   - *Google Assistant:* should already say *Off*, because of `disable_assistant=true`. If not, press OK on it.
   - *Lighter home screen:* press OK until it says *On*.
4. **Turn off apps:**
   1. Go back up to the first row and press OK. It lists the TV's apps, each with its memory use and *On* or *Off*.
      The first time, it's still looking for apps: wait.
   2. Page through it with DOWN, taking screenshots.
   3. Send me one list: the apps you'd turn off (preinstalled ones I'm unlikely to use, like games, shopping, extra
      video stores, demos and promo channels), and the ones you'd keep.
   4. Turn off only the ones I approve, with OK on each.

   Rows that say *Keeps your Wi-Fi*, *Your keyboard*, *Accessibility*, *Needed for casting* or *Off before Lumen*
   can't be changed. Everything turned off here comes back with *Original*.

## 11. Finish

1. **Report after.** Run `tools/tv-report.sh <IP> after`. Tell me what changed compared to `before`: free memory, the
   home screen's memory, and what's off.
2. **Tell me:**
   - Going back is *Lumen* → *Home Screen* → *Original*.
   - The apps on the home screen and the round top buttons are set in *Lumen* → *Shelf* and *Top Buttons*.
   - Keep `~/Lumen-backup` (the signing key and the settings).
   - Updates, which keep everything as it is: in the repo folder, run `git pull`, then `./build.sh` and
     `adb install -r Lumen.apk`. Without git: rename the old folder, download the code again as in step 1, copy
     `config.properties` and `lumen.keystore` into it from `~/Lumen-backup`, move `.tools/` over from the old folder
     (then run `tools/setup.sh --accept-android-sdk-license`, which only fixes its paths), and build and install.
   - USB debugging stays on: Lumen uses the TV's own debugging to check itself after restarts and to apply Speed.
   - If the TMDB key was empty and I add it later, run `./build.sh` and `adb install -r Lumen.apk` again.
3. **Disconnect:** `adb disconnect`.
4. **Summarize** in a few lines: what's on, what's off, and anything that still needs me.

## If it stops

- **"the TV's first setup saved the Wi-Fi…":** the switch stopped before changing anything.
  1. Ask me to forget the network and connect again in Settings → Network & Internet.
  2. Saved networks that are out of range don't show there. Find the ones with
     `cname=com.google.android.tungsten.setupwraith` in `adb shell dumpsys wifi` (each block starts with its `ID:`).
  3. With my OK, remove those with `adb shell cmd wifi forget-network <id>`.
  4. Do step 9 again.
- **"I couldn't check which app saved the Wi-Fi…":** wait a minute and do step 9 again.
- **"On this TV model, switching the home screen kept a TV from starting…":** stop. Lumen stays an app on this TV.
  Tell me.
- **Lumen's home screen crashes 3 times in a row:** the TV gets its own home screen back by itself. Send me
  `adb logcat -d -s Lumen AndroidRuntime | tail -n 200`.
- **Anything else:** take a screenshot, read `adb logcat -d -s Lumen | tail -n 100`, and tell me what you see before
  doing more.

## Recovery: the old app was removed while it was on (only with my OK)

Use this when `io.freemytv` is gone but `adb shell settings get global free_my_tv_disabled` still lists packages.

1. **Show me the list.** Find the TV's own home screen in it:
   - `com.mitv.tvhome.atv` on Xiaomi's PatchWall;
   - `com.google.android.apps.tv.launcherx` on Google TV;
   - `com.google.android.tvlauncher` on Android TV.
2. **Turn things back on, home screen first:** `adb shell pm enable <home package>`, then each other package in the
   list except `com.google.android.tungsten.setupwraith`.
3. **Make it the home screen again.**
   `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
   lists the home screens. Set the TV's own with `adb shell cmd package set-home-activity '<package/activity>'`.
4. **The setup app last.** Only now, if `com.google.android.tungsten.setupwraith` is in the list, run
   `adb shell pm enable com.google.android.tungsten.setupwraith`. Then set the home activity again: a new home
   candidate can drop the default.
5. **Screensaver.** Read `adb shell settings get secure screensaver_default_component`. Put that value into
   `screensaver_components` (`adb shell settings put secure screensaver_components <value>`). If it's `null`, run
   `adb shell settings delete secure screensaver_components` instead.
6. **Animations.** If `adb shell settings get global free_my_tv_animations` shows three values, they are, in order,
   `window_animation_scale`, `transition_animation_scale` and `animator_duration_scale`.
   - Put each back with `adb shell settings put global <name> <value>`. A `null` means
     `adb shell settings delete global <name>`.
   - Then run `adb shell settings delete global free_my_tv_animations`.
7. **Finish up.** Run `adb shell settings delete global free_my_tv_disabled`. Press Home and take a screenshot. Then
   continue at step 8.
