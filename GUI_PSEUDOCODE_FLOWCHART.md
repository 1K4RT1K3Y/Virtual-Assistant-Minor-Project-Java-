# VAMP — Concise GUI Pseudocode

Overview
- Java Swing GUI to add/view/edit daily tasks, send reminders, and show notifications.
- In-memory schedules: Map<LocalDate, List<Task>>. Task: {id, title, startTime, endTime, duration, done}.

Startup
- Main: set LookAndFeel, create `VirtualAssistantGUI` on EDT.
- Init: schedules, system tray (optional), UI (header, task list, buttons), clock timer, RGB timer, reminder daemon.
- After 300ms show name dialog (Save/Skip). Save displays greeting then replaces it after 5s.

Primary UI
- Header: gradient, greeting label (hidden initially), textual clock (updates every second).
- Center: `TaskListPanel` (today's tasks).
- Footer: Buttons — Add Task, View Schedule, Launch App.

Add Task (modal)
- Inputs: Title, Date (Y/M/D spinners), Start time (H/M spinners), Duration (minutes).
- Add: validate title, build LocalDate/LocalTime, compute endTime, create Task, add to schedules[date], refresh UI, notify, close.
- Cancel: close without saving.

View Schedule (modal)
- Left: date selector; Right: scrollable task list for selected date.
- Rebuild list on date change. Each row shows title/time + Edit/Delete.
- Delete: confirm → remove from schedules → refresh.

Edit Task (modal)
- Pre-fill fields; on Save validate title/time (HH:mm), update task fields, refresh UI, close.

Reminder Thread (daemon, every 30s)
- Track `remindedStarts` (synchronized) and `focusedStart` epoch.
- For today's tasks (sorted): skip done. Compute taskStart/taskEnd and remindAt = taskStart - 5min.
- If now in [remindAt, taskStart) and id not reminded: show 5-min notification, play sound, add id to set.
- If now in [taskStart, taskEnd): mark in-progress.
- If continuous focus >= 3600s: notify break and reset focus timer.

Notifications & Sound
- Use SystemTray.displayMessage if supported; attempt to play `notification.wav` (ignore errors).

Styling & Effects
- Dark theme via `Colors` constants. Buttons styled with rounded gradients. Header hue rotates via HSB timer (50ms).

Threading & Safety
- UI updates on EDT. Reminder runs in background (daemon). `schedules` accessed on EDT; `remindedStarts` synchronized.

Persistence
- Runtime-only currently; suggest JSON save/load on exit/startup as an improvement.

Error Handling (short)
- Validation/parsing errors: show message and keep modal open.
- SystemTray/audio errors: log/skip, do not crash UI.
- Reminder thread: handle InterruptedException and log other exceptions, continue loop.

Quick Flow Summary
1) Start → init UI and threads → show name dialog.
2) User adds/edits/deletes tasks; tasks stored in schedules map.
3) Reminder thread checks tasks every 30s: send 5-min reminders and 1-hour break prompts.
4) Notifications via system tray; sound optional.

This concise version preserves the original program flow and behaviors while removing verbose examples and repetition.
# Virtual Assistant (VAMP) - Complete GUI Pseudo Code

## System Overview
V.A.M.P is a task scheduling application with GUI built using Java Swing. Users manage daily tasks, receive reminders, and track productivity.

---

## COMPLETE PROJECT WORKING

### PHASE 1: APPLICATION INITIALIZATION
```
MAIN ENTRY POINT:
  START
  │
  ├─ Load System Look and Feel
  ├─ Create VirtualAssistantGUI Window Instance
  ├─ Initialize schedules Map (empty)
  ├─ Initialize UI Components
  │   ├─ Create Header Panel (Gradient background)
  │   │   ├─ Left: Greeting Label (hidden initially)
  │   │   └─ Right: Digital Clock (updates every 1s)
  │   ├─ Create Task List Panel (main content)
  │   └─ Create Button Panel
  │       ├─ "Add Task" button
  │       ├─ "View Schedule" button
  │       └─ "Launch App" button
  ├─ Setup System Tray Icon (if supported)
  ├─ Start Background Threads
  │   ├─ Clock Update Thread (every 1 second)
  │   └─ Reminder Thread (every 30 seconds, daemon)
  ├─ Schedule Timer for startup dialogs
  │   └─ After 300ms: Show Name Dialog
  └─ Display Window to User
```

### PHASE 2: STARTUP & USER GREETING
```
STARTUP NAME DIALOG:
  Show Modal Dialog: "Welcome"
  │
  ├─ Display: "What's your first name?"
  ├─ Create Text Field for input
  └─ Create 2 Buttons: "Save" | "Skip"
  
  WAIT FOR USER ACTION:
    │
    ├─ IF User Clicks "Save":
    │   ├─ Get input text (trim whitespace)
    │   ├─ IF text is empty:
    │   │   └─ Do nothing, keep dialog open
    │   └─ IF text not empty:
    │       ├─ Extract first name (first token)
    │       ├─ Show Welcome Greeting:
    │       │   ├─ Display "Welcome To VAMP [Name]!" (48pt bold)
    │       │   ├─ Set Greeting Label Visible = true
    │       │   ├─ Start 5 second timer
    │       │   └─ After timer expires:
    │       │       └─ Replace text with "Upgrade Your Efforts!"
    │       └─ Close dialog
    │
    └─ IF User Clicks "Skip":
        ├─ Keep Greeting Label hidden
        ├─ Keep Clock visible
        └─ Close dialog
```

### PHASE 3: MAIN APPLICATION LOOP - USER INTERACTIONS
```
MAIN EVENT LOOP:
  WHILE Application Running:
    │
    WAIT FOR USER ACTION (non-blocking)
    │
    ├─ IF User Clicks "Add Task" Button
    │   └─ CALL: SHOW_ADD_TASK_DIALOG()
    │
    ├─ IF User Clicks "View Schedule" Button
    │   └─ CALL: SHOW_VIEW_SCHEDULE_DIALOG()
    │
   Replace long, verbose pseudocode with a concise version that preserves application flow and behavior.
    │   └─ CALL: SHOW_APP_LAUNCHER_DIALOG()

    │
