# Pluck

![Pluck](Pluck.png)

**Real places. Entirely fictional stories.**

Pluck is a local-first Android app for turning a day of captured places into one continuous piece of fiction. Capture a photo at each stop, keep the sequence, choose a mood, and let Pluck write a story in which every place matters to the plot.

There is no AI chat interface, no account, no advertising, no analytics, and no Pluck backend.

## Demo

<p align="center">
  <a href="demo/Screenshot_20260721_175752_Pluck.png"><img src="demo/Screenshot_20260721_175752_Pluck.png" alt="Pluck home screen" width="22%" /></a>
  <a href="demo/Screenshot_20260721_175756_Pluck.png"><img src="demo/Screenshot_20260721_175756_Pluck.png" alt="An empty journey" width="22%" /></a>
  <a href="demo/Screenshot_20260721_232747_Pluck.png"><img src="demo/Screenshot_20260721_232747_Pluck.png" alt="A journey in progress" width="22%" /></a>
  <a href="demo/Screenshot_20260721_232947_Pluck.png"><img src="demo/Screenshot_20260721_232947_Pluck.png" alt="A journey ready for story generation" width="22%" /></a>
</p>

<p align="center">
  <a href="demo/Screenshot_20260721_175801_Pluck.png"><img src="demo/Screenshot_20260721_175801_Pluck.png" alt="Saved stories library" width="22%" /></a>
  <a href="demo/Screenshot_20260721_233005_Pluck.png"><img src="demo/Screenshot_20260721_233005_Pluck.png" alt="Saved journey timeline" width="22%" /></a>
  <a href="demo/Screenshot_20260721_175823_Pluck.png"><img src="demo/Screenshot_20260721_175823_Pluck.png" alt="Secure provider key settings" width="22%" /></a>
  <a href="demo/Screenshot_20260721_175905_Pluck.png"><img src="demo/Screenshot_20260721_175905_Pluck.png" alt="Local Gemma readiness" width="22%" /></a>
</p>

<p align="center">
  <a href="demo/Screenshot_20260722_003209_Pluck.png"><img src="demo/Screenshot_20260722_003209_Pluck.png" alt="Reality versus Fiction story reader" width="22%" /></a>
  <a href="demo/Screenshot_20260722_003250_Pluck.png"><img src="demo/Screenshot_20260722_003250_Pluck.png" alt="Private story sharing options" width="22%" /></a>
  <a href="demo/Screenshot_20260722_003252_Pluck.png"><img src="demo/Screenshot_20260722_003252_Pluck.png" alt="Story save and export options" width="22%" /></a>
  <a href="demo/Screenshot_20260722_003304_IntentResolver.png"><img src="demo/Screenshot_20260722_003304_IntentResolver.png" alt="Android share target chooser for a Pluck story card" width="22%" /></a>
</p>

More detailed captures: [full settings](demo/Screenshot_20260721_175806_Pluck.png), [20-place journey timeline](demo/Screenshot_20260721_175833_Pluck.png), [journey-ready state](demo/Screenshot_20260721_232821_Pluck.png), and [current settings view](demo/Screenshot_20260721_232850_Pluck.png).

## Built with Codex

Pluck was designed and iterated in a sustained collaboration with **GPT-5.6 in Codex**. Codex was not used as a one-shot code generator: it worked alongside the builder through product decisions, UI critique, implementation, debugging, and verification, while the builder retained ownership of scope, privacy choices, and final UX direction.

### Where Codex accelerated the project

- **Architecture and product shaping.** Starting from the idea of turning real places into fictional stories, Codex helped turn the concept into a local-first Android plan: Compose + Material 3, MVVM, Hilt, Room, CameraX, app-private media storage, and a provider abstraction that keeps cloud models and Local Gemma behind the same story-generation contract.
- **Fast, intentional UI iteration.** The builder set the creative direction—minimal, expressive, native Android, no chat surface, dynamic theming, AMOLED support, haptics, and an emotionally calm reader. Codex translated feedback into reusable Compose components and repeatedly refined the Home, Journey, Library, Story Reader, Settings, loading, and navigation experiences.
- **Feature implementation.** Codex accelerated the work behind the polished surface: multi-provider story generation, Local Gemma readiness and model management, story revisions without chat, Reality vs Fiction reading, widgets, PDF/EPUB export, share cards, multi-day novella arcs, offline TTS, saved-journey generation, and gallery import.
- **Engineering and debugging.** Codex traced build and runtime failures from Android Studio/Logcat output, including a stale JDK `jlink` configuration and a LiteRT-LM coroutine mismatch. It also verified builds after changes and kept new gallery photos private by normalizing them into app-owned JPEG files instead of relying on temporary picker URIs.
- **Quality and privacy review.** The builder made the key trade-offs: no accounts, no Pluck backend, no ads or analytics, user-owned cloud API keys, and an optional offline Local Gemma path. Codex helped surface privacy implications around location metadata, cloud generation, exports, and model downloads so those choices could be explicit in the app and README.

### Human decisions, Codex leverage

The builder chose what Pluck should feel like and what it should not become: a magical storytelling tool rather than a journal, travel log, or AI chat app. The builder reviewed real device results, supplied visual direction and screenshots, selected the final feature set, and decided the privacy boundaries. GPT-5.6 and Codex made the loop dramatically faster by preserving project context, inspecting the existing codebase before making changes, proposing focused implementations, applying those changes, and compiling the Android project to validate them.

## What you can do

- Capture a photo, timestamp, optional coordinates, and approximate address at each place.
- Follow the day in an animated timeline, including a private on-device route sketch when two or more captures have location data.
- Generate a fictional story from the ordered journey with Gemini, OpenAI, Claude, Groq, Together AI, OpenRouter, or Local Gemma.
- Choose a narrative mood: Cinematic, Mysterious, Whimsical, Warm, Adventurous, or Dark.
- Add optional fictional direction such as genre, protagonist name, and companions. These are writing instructions only; Pluck never treats them as image-identification facts.
- Regenerate without chat using concise choices such as **More mysterious**, **Shorter**, **More emotional**, or **Change mood**.
- Read stories in a mood-aware reader with a generated typographic cover, progress tracking, Reality vs Fiction references, and local audiobook controls.
- Browse past journeys grouped by week, month, mood, or season.
- Group daily journeys into a continuing multi-day novella with a shared fictional world and continuity hand-off between chapters.
- Export one story or a month of stories as a PDF book or EPUB, with selected photos, chapter headers, deterministic cover art, and a private route sketch.
- Create local share cards in portrait, Instagram Story, or X/Twitter landscape formats. Shared images omit photo metadata.
- Add one of Pluck’s private home-screen widgets: **Capture next place** for Local Gemma readiness and camera access, **Today’s journey** for a live capture count, or **Latest story** for a short reading preview. Widgets never show photos, addresses, or route data.
- Open a bundled **20-place demo journey** made from original illustrations. It seeds images and fictional local metadata only—never a prewritten story—so generation remains live for presentations.

## Privacy at a glance

| Area | Local Gemma | Cloud provider |
| --- | --- | --- |
| Photos, route, and story database | Stored on this device | Stored on this device |
| Story-generation input | Never leaves the device | Sent directly to the provider selected by the user |
| API key | Not required | Stored in Android Keystore-backed encrypted preferences |
| Internet after setup | Not required | Required |
| Pluck account or server | Never used | Never used |

When a cloud provider is selected, the ordered images and the story prompt (which can include time and location hints) are sent to that provider’s API. Choose Local Gemma when you want the complete generation path to stay on-device.

## Minimum device requirements

### Core app

- **Android 7.0 / API 24 or later.**
- A usable **rear camera** for in-app capture, or access to the system photo picker for building a journey from existing photos.
- **Camera permission** is needed only for capturing inside Pluck. Gallery import does not request broad media-storage permission.
- **Approximate location permission** is optional. It adds place hints, approximate addresses, and the private route sketch.
- Enough free storage for captured photos, generated books, share cards, and saved stories. Core storage use grows with the number of photos and exports; Pluck does not impose a fixed core-storage threshold.
- Internet is needed only for cloud story providers, provider connection tests, and the initial Local Gemma download. Existing journeys, story reading, local exports, and verified Local Gemma generation work offline.

### Local Gemma (experimental)

- A capable modern Android phone is recommended. Pluck is tuned and demonstrated on the **Samsung Galaxy S23**.
- **4.5 GB of free private device storage** is enforced before installation. The verified Gemma 4 E2B download is approximately **2.58 GB**; remaining space allows safe installation and the LiteRT-LM runtime cache.
- A stable internet connection is required once to download and verify the model. After verification, Local Gemma can generate stories without a connection.
- Google estimates approximately **1.1 GB** of model memory for the Gemma 4 E2B mobile variant. This is a model-planning estimate, not a guarantee that every Android 7.0+ phone can run Pluck’s multimodal workload. [See Google’s Gemma 4 memory guidance.](https://ai.google.dev/gemma/docs/core)
- GPU acceleration and LiteRT-LM support vary by device. Pluck does not claim a fixed RAM, chipset, or GPU minimum for Local Gemma; it remains optional and is not guaranteed on every supported Android device.
- On a Galaxy S23, a typical four-place, 700–1,200-word story takes roughly **3–6 minutes**. More images, longer output, low battery, or a warm device can increase this time.
- Start local generation with **30% battery or more**. For longer stories, connect power when possible and let a warm phone cool first.

### Optional platform features

- **Dynamic Material You colors:** Android 12+; Pluck falls back gracefully on older supported Android versions.
- **Audiobook mode:** an installed Android text-to-speech engine with an offline voice for the selected language.
- **Home-screen widgets:** a launcher that supports Android app widgets.

## Local Gemma

Local Gemma is experimental and designed for capable devices such as the Samsung Galaxy S23. It uses Google AI Edge LiteRT-LM with the pinned **Gemma 4 E2B Instruct** LiteRT-LM release.

1. Open **Settings → Local AI**.
2. Review the storage requirement and explicitly confirm the download.
3. Pluck resumes the fixed HTTPS download when supported, checks its SHA-256 digest, and only then installs it in app-private no-backup storage.
4. Select **Local Gemma** as the preferred provider.
5. Generate stories in airplane mode.

The app does not bundle a model, accept a manual model URL, import a model file, or run an unverified model. The fixed model manifest is compiled into the app: repository, revision, filename, and SHA-256 cannot be changed in Settings. The distribution route is the Google-documented LiteRT Community channel hosted on Hugging Face—not a user-provided or arbitrary mirror. See the [Google AI Edge LiteRT-LM Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) for the supported model-distribution guidance.

The current download is about **2.58 GB**; Pluck asks for about **4.5 GB** of free private storage to allow a safe installation and runtime cache. The Local AI screen shows readiness, storage, verification state, progress, update availability, and device guidance. A newer release is reported, but a new pinned integrity manifest is delivered with an app update—Pluck never silently switches to an unpinned model.

## Story-generation flow

```mermaid
flowchart TD
    A[Ordered journey photos] --> B[Local metadata preparation]
    B --> C{Selected provider}
    C -->|Cloud| D[Downsample and send directly to the selected API]
    C -->|Local Gemma| E[Verify model and run LiteRT-LM on device]
    D --> F[Structured story response]
    E --> F
    F --> G[Parse title, story, scene references, and continuity]
    G --> H[Save locally in Room]
    H --> I[Read, revise, narrate, export, or share]
```

The shared prompt contract asks the provider to analyze the entire ordered image sequence, retain fictional continuity, avoid a photo-by-photo diary, and return `TITLE`, `STORY`, and `CONTINUITY` fields. Story paragraphs may carry scene markers so the Reality vs Fiction reader can relate a fictional passage to its captured place.

## App structure

```text
app/src/main/java/com/example/pluck/
├── data/
│   ├── database/       Room entities, DAOs, and migrations
│   ├── export/         Local PDF and EPUB creation
│   ├── localai/        LiteRT-LM model management and local provider
│   ├── narration/      Offline Android Text-to-Speech adapter
│   ├── provider/       Cloud StoryProvider implementations
│   └── repository/     Room and settings repositories
├── domain/             Models, repository contracts, use cases
├── ui/                 Compose screens, components, theme, share renderer
├── viewmodel/          Immutable StateFlow UI state
├── navigation/         Navigation Compose graph
├── widget/             Capture, Today Journey, and Latest Story home-screen widgets
└── di/                 Hilt bindings
```

The UI talks to use cases and repositories, never to the LiteRT-LM engine or a provider client directly. Every generation path implements the same `StoryProvider` contract, which keeps the UI independent from the selected model.

## Local data model

All persistent app data is stored in a Room-managed SQLite database on the device.

| Entity | Purpose |
| --- | --- |
| `journeys` | One daily journey, keyed by date and time zone. |
| `journey_photos` | Local image path, capture time, optional coordinates, and address. |
| `stories` | Fictional story text, provider, mood, and optional creative settings. |
| `story_scenes` | Local link between a story paragraph and a captured photo. |
| `novella_arcs` | Multi-day fictional arc settings and dates. |
| `novella_chapters` | Ordered journey-to-chapter membership, story, and continuity summary. |

Migrations preserve existing journeys and stories. In particular, the v2 → v4 migration adds creative direction, scene references, and novella data without destructive database resets.

## Cloud providers and keys

Pluck supports these user-configured providers:

- Google Gemini
- OpenAI
- Anthropic Claude
- Groq
- Together AI
- OpenRouter

Only the selected cloud provider needs a key. Each provider has its own field and connection test in Settings. Keys are stored with `EncryptedSharedPreferences`, backed by an Android Keystore `MasterKey`; the app never ships a key.

## Interface and accessibility

The UI is built with Jetpack Compose and Material 3. It supports dynamic color, system/light/dark themes, edge-to-edge layouts, adaptive widths, expressive tonal surfaces, haptic preferences, and reduced-distraction motion. All primary interactions maintain a 48dp touch target and use accessible labels.

## Build and run

### Development requirements

- Android Studio with Android SDK 36 installed
- Android Studio’s bundled JBR 21
- Android 7.0 / API 24 or newer device or emulator
- A physical Android device for CameraX, location, Local Gemma, widget, and TTS verification

### Debug build

```bash
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it from Android Studio or with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First-run checklist

1. Complete the minimal onboarding and optionally enter a preferred name.
2. Grant camera permission; grant location permission if you want address and private route features.
3. In Settings, choose either a cloud provider and enter its API key, or download Local Gemma.
4. Start today’s journey, capture at least two places, then generate a story.

### Hackathon demo journey

The Home screen offers **Explore the 20-photo demo journey** and **Create a story from demo photos**. Both actions open the same local, asset-backed journey with twenty original illustrated places, fictional timestamps, synthetic route coordinates, and fictional addresses. It deliberately creates no `Story` record: the second action simply opens mood selection directly, so the selected provider still performs the live generation without personal photos.

## Technology

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- MVVM, repository pattern, clean layer boundaries, Hilt
- Room 2.7.2, Coroutines, StateFlow, Kotlinx Serialization
- CameraX 1.5.0, Fused Location Provider, Android Geocoder
- Retrofit 2.11.0, OkHttp, Coil
- AndroidX Security Crypto for encrypted settings
- Google AI Edge LiteRT-LM 0.14.0 for optional on-device inference
- Android `TextToSpeech` restricted to installed offline voices

## Validation still recommended before release

The project builds as a debug application. Before publishing, validate the complete flow on a real target device—especially the S23—for model download, verified offline generation, camera/location permissions, thermal behavior, export/open flows, sharing, offline TTS voices, and each cloud provider with a real user key. Add regression coverage for Room migrations and model download/verification failures before treating a release build as production-ready.

---

**Project by Hariom Sharnam**
