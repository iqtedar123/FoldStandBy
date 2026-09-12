# Play Console — `FOREGROUND_SERVICE_SPECIAL_USE` declaration

## Video file

Upload **`fgs-special-use-demo.mp4`** (device screen recording from a Galaxy Z Fold) to YouTube (unlisted) or Google Drive (anyone with the link can view), then paste that URL into the Play Console declaration form.

Path: `play-store/video/fgs-special-use-demo.mp4`

### What the video shows

1. Open FoldStandBy settings
2. Turn on **Enable nightstand** (starts `PostureMonitorService` as a `specialUse` foreground service)
3. Notification shade: ongoing notification **“FoldStandBy is watching for tent mode”**
4. Nightstand UI (clock / weather / calendar) presented by the service when posture/conditions match
5. Service remains user-visible via the ongoing notification (Stop action on the notification)

---

## Suggested form answers

### Foreground service type

`specialUse` / Special use

### Describe the user-facing feature that requires this foreground service

FoldStandBy’s core feature is a lock-screen nightstand display for folding phones. When the user enables nightstand, `PostureMonitorService` runs as a foreground service so it can continuously observe foldable tent/cover posture and keyguard state, then immediately present the nightstand UI (clock, weather, upcoming calendar events, alarms) while the device is locked and in tent or cover posture. This monitoring must stay active after the user leaves the app and cannot be deferred to a one-shot job.

Manifest property (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`):
> Monitor foldable tent/cover posture to present a lock-screen nightstand display.

### Why other FGS types do not apply

This is not media playback, location tracking for maps, connected-device I/O, camera/mic capture, data sync, or a short critical task. The work is continuous posture/keyguard monitoring for a user-initiated always-on nightstand experience, which is why `specialUse` is declared.

### User impact if the task is deferred or interrupted

If the service is stopped or delayed, FoldStandBy cannot detect when the phone is folded into tent mode or showing the cover display while locked, so the nightstand screen will not appear when the user expects it. Alarms and glanceable clock/weather/calendar on the nightstand would also fail to show promptly.

### How the user starts and stops the service

- **Start:** User opens FoldStandBy and turns on **Enable nightstand** (user-initiated). An ongoing notification is shown.
- **Stop:** User turns **Enable nightstand** off in the app, or taps **Stop** on the ongoing notification.

### Video link

*(paste YouTube unlisted or Drive link after upload)*
