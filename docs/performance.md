# Performance notes

Targets: **60 fps D-pad navigation, cold start < 2 s** on a TV SoC. Measure on the real TV, not an emulator.

## Design decisions that keep it fast

- **No live backdrop blur by default.** The orbs are radial gradients that fade to transparent, so they already look blurred, at the cost of four gradient fills per frame. Glass panels are translucent fills over them, so you get the frosted look with no `RenderEffect`, no offscreen buffers, and no per-frame blur.
- **Draw-phase animation.** The orb drift value is read only inside `Canvas`'s draw lambda, so animating causes no recomposition or layout. *Reduce motion* stops it entirely.
- **Opt-in `RenderEffect` blur** (Settings → Appearance → *Extra background blur*, API 31+) for testing only. Keep it only if `gfxinfo` (below) shows no jank.
- **Icons/banners** load lazily off the main thread into a 24 MB LRU cache, and pre-render to bitmaps sized for the tile.
- **Clock** recomposes once per minute, aligned to the minute boundary.
- **Release build** (R8 + resource shrinking + Compose baseline profiles) is what `scripts/deploy.sh` installs by default. Debug Compose builds are several times slower; never judge smoothness on a debug build.
- Fonts are static TTF instances (not variable fonts), so text shaping costs less.

## How to measure on the TV

```sh
# Cold start (TotalTime should be < 2000 ms):
adb shell am force-stop dev.glasstv.launcher
adb shell am start -W -n dev.glasstv.launcher/.MainActivity

# Frame pacing: reset, use the D-pad around the grid for ~20 s, then dump.
adb shell dumpsys gfxinfo dev.glasstv.launcher reset
adb shell dumpsys gfxinfo dev.glasstv.launcher | grep -E "Janky frames|50th|90th|99th"
```

Healthy at 60 Hz: 90th percentile ≤ 16 ms and janky frames ≤ 5 %. Measure with *Extra background blur* off, then on. If the numbers get worse, leave it off. If you see jank with it off, turn on *Reduce motion* and measure again to isolate the orbs.
