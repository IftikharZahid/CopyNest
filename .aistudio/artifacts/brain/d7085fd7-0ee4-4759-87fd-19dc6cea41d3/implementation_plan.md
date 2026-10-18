# CopyNest — Smart Clipboard Notebook for Android

CopyNest is a lightweight, privacy-conscious clipboard notebook for Android that automatically captures text copied from other apps when resumed, stores it securely using Room database, and provides robust organization, search, pin, edit, copy, and export features.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your preferences, the following core product choices are confirmed:
> - **Aesthetic**: Clean minimal light and dark theme with refined tonal surfaces.
> - **Default Sorting**: Newest clips first.
> - **Clipboard Capture**: Automatic detection when the app opens and resumes, with a manual Save fallback and duplicate prevention.

---

## 1. Overview & Core Concept

- **What It Does**: Automatically captures clipboard items when the app opens/resumes, stores them in a local Room database, and allows users to browse, search, pin, edit, tag, copy back, export, and delete clips.
- **Target Audience / Persona**: Students, professionals, and power users who frequently copy text, links, code snippets, and messages across apps.
- **Key Value**: Local-first privacy, zero data leaves the device, lightning-fast search, duplicate prevention, and clean M3 design.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home / Clipboard Inbox**: Shows saved clips with search bar, filter chips (All, Recent, Pinned, Links), type indicators, timestamp, and manual "Save Clipboard" FAB.
  2. **Clip Details Sheet / Screen**: Full readable text view with 1-tap Copy, Pin/Unpin, Edit, Delete with confirmation, and character/word count.
  3. **Search & Filter**: Real-time filtering by keyword and category/type (Text, Link, Code, Note).
  4. **Settings**: Toggle auto-capture on resume, theme selection (Light/Dark/System), local backup/export (JSON/TXT), and clear all clips with confirmation.
- **Visual Identity & Theme**:
  - *Aesthetic*: Clean minimal modern Android (Material 3).
  - *Color Palette*: Slate & Neutral tonal surfaces with a refined indigo/teal accent.
  - *Typography*: Clean sans-serif hierarchy with `sp` scaling and monospace font for code clips.
  - *Empty States*: Friendly, uncluttered empty states with actionable tips.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Clipboard Capture on Resume**
  - *Chosen Approach*: Check `ClipboardManager` upon `onResume()` and app startup.
  - *Why*: Reliable within modern Android platform restrictions without requiring intrusive permanent background services or Accessibility permissions.
- **Decision 2: Local Persistence with Room**
  - *Chosen Approach*: Room Database with Flow and Kotlin Coroutines.
  - *Why*: Fast, offline-first, type-safe, and robust for personal notebook storage.
- **Decision 3: Duplicate Prevention**
  - *Chosen Approach*: Compare new clipboard text with the most recent record in Room; if identical, skip insertion.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│  (HomeScreen, DetailScreen, SearchScreen, Settings)    │
└───────────────────────────┬────────────────────────────┘
                            │ observes Flow / invokes actions
┌───────────────────────────▼────────────────────────────┐
│                    ClipboardViewModel                  │
│       (Manages UI state, Clipboard Check, Filters)     │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                  ClipboardRepository                   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                     ClipDao & Room                     │
│                  (Local SQLite DB)                     │
└────────────────────────────────────────────────────────┘
```

- **Data Model (`ClipEntity`)**:
  - `id`: Long (PrimaryKey, autoGenerate)
  - `text`: String (Copied content)
  - `timestamp`: Long
  - `isPinned`: Boolean
  - `contentType`: String (TEXT, URL, CODE, NOTE)
  - `category`: String?
- **Interactive Component Mapping**:
  - Auto-capture triggers on `LifecycleEventObserver` (ON_RESUME).
  - Manual capture button on Home screen.
  - Search query state reactive filtering via Flow.
