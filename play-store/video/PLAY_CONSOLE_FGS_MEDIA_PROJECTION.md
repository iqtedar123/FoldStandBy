# Play Console — `FOREGROUND_SERVICE_MEDIA_PROJECTION` declaration

## Video file

Upload **`fgs-media-projection-demo.mp4`** (device screen recording from a Galaxy Z Fold in tabletop App Pair) to YouTube (unlisted) or Google Drive (anyone with the link can view), then paste that URL into the Play Console declaration form.

Path: `play-store/video/fgs-media-projection-demo.mp4`

### What the video shows

1. FoldStandBy **Flex Reflection** on the lower App Pair pane, with a video app (YouTube) on the upper pane
2. User starts capture (**Start capture**)
3. System MediaProjection consent: **Share your screen with FoldStandBy?** with **Share one app**
4. System picker: **Choose app to share** — user selects the paired YouTube window (not the entire screen)
5. `ReflectionCaptureService` runs as a `mediaProjection` foreground service and mirrors the chosen app onto the lower pane as a frosted ambient reflection
6. In-app **Stop capture** is available while reflecting; closing Flex Reflection also stops the service

---

## Suggested form answers

### Foreground service type

`mediaProjection` / Media or content projection

### Use case

Media or content projection / streaming or recording with the MediaProjection API

### Describe the user-facing feature that requires this foreground service

Flex Reflection is a tabletop feature for folding phones. The user App Pairs a video app on the upper half with FoldStandBy on the lower half, then starts capture. After the system MediaProjection consent and app picker, `ReflectionCaptureService` uses the MediaProjection API to create a virtual display and mirror the chosen app window onto the lower pane as a blurred ambient reflection, with optional media controls. Android requires this capture session to run in a `mediaProjection` foreground service.

### Why this FGS type is required

The work is live projection of another app’s content via `MediaProjection` / `VirtualDisplay`. That is the `mediaProjection` foreground-service type. It is not background media playback, location, connected-device I/O, camera/mic capture, or data sync.

### User impact if the task is deferred or interrupted

If the service cannot run, Flex Reflection cannot keep the MediaProjection session or virtual display alive, so the lower pane cannot show the ambient mirror of the paired video app. The tabletop reflection experience would stop as soon as the user leaves the activity.

### How the user starts and stops the service

- **Start:** User opens FoldStandBy → **Reflections** → **Open Flex Reflection** → **Start capture**, then confirms the system **Share your screen** prompt and picks the paired app window.
- **Stop:** User taps **Stop capture** in Flex Reflection, taps **Stop capture** on the ongoing “Reflecting paired app video” notification, or closes Flex Reflection.

### Video link

*(paste YouTube unlisted or Drive link after upload)*
