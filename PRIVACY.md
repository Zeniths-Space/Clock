# Privacy

- No internet permission: the app cannot send anything anywhere.
- No accounts, ads, analytics or telemetry.
- Alarms (time, days, name, vibration, a pending snooze) are kept in the app's own device-protected
  storage (`alarms.json`), so they can ring before the phone is first unlocked after a restart. That
  storage is not encrypted with your lock screen credentials; nothing sensitive is in it. Uninstalling
  or clearing the app's data deletes it. Android backup is off.
- Permissions, each only for alarms: notifications (the alarm rings through one), full-screen alarms
  over the lock screen, exact alarms, and starting after a restart to set alarms again.
- Sunrise and sunset are computed on the phone from a city's coordinates; no location is read.
