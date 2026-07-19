# Pluck

Turn your journey into a story.

## What is this

Pluck is an Android app. During the day, you take one photo whenever you arrive somewhere new — a cafe, a park, a train station, anywhere. You don't write anything or explain anything. Just the photo.

When you're done for the day, Pluck looks at all the photos together, in order, and writes a made-up story out of them. Each photo becomes a scene in the story. The places are real. The story is not.

The same walk through the same three places could become a fantasy quest one day and a detective mystery the next. The app decides the genre and the plot based on the mood and content of your photos, so no two stories come out the same way.

Pluck is not a journal or a photo gallery. It does not try to describe what happened to you. It imagines what could have happened, using your day as the raw material.

## Why this exists

Most days disappear. You walk through a dozen places and remember almost none of them individually. Pluck does not try to help you remember better. Instead it turns those forgettable moments into something worth looking back at — a short story, unique to that day, that you would never have written yourself.

## How it works

There are three steps, and they happen automatically, one after another, once you finish a journey and tap generate.

**Step 1: Look at each photo**
Every photo is sent to an AI vision model, which figures out what kind of place it is, what the lighting and mood look like, roughly what time of day it is, and a few specific details worth remembering (a red umbrella, empty benches, rain on a window, whatever stands out).

**Step 2: Plan the story**
All of those photo descriptions, in the order you took them, are handed to the AI as a full sequence. At this stage, nothing gets written yet. The AI first decides: what genre fits the mood of this day, who the main character is, what the overall plot is, and what job each individual photo/scene does in that plot. This is basically an outline, and it's what keeps the final story feeling like one connected adventure instead of five random paragraphs.

**Step 3: Write each chapter**
Using that outline, the AI writes the actual chapter for each photo — real prose, grounded in the specific visual details noticed in step 1, but shaped by the plot decided in step 2. Each chapter also knows what happened in the previous ones, so characters and plot threads carry through the whole story.

The result is a short book made from your day: one photo per chapter, one story per journey.

## What you can do in the app

- Take photos throughout the day to build up a journey
- Finish the journey and generate a story from it
- Read the story chapter by chapter, each one paired with the photo that inspired it
- Look back at a library of past journeys, each saved as its own story
- Use your own free Gemini API key, so there's no forced account or subscription

## Tech stack

- Platform: Android, built with Kotlin and Jetpack Compose
- Architecture: MVVM
- Local storage: Room database, so your photos and stories stay on your device
- Camera: CameraX
- AI: Google Gemini API (vision model for reading photos, text model for writing the story), using your own API key
- Async work: Kotlin Coroutines and Flow
- Dependency injection: Hilt

## Built with Codex

This project was built using Codex (GPT-5.6) inside a Codex session. Codex was used to design the data models, build the Room database layer, write the three-stage story pipeline (scene extraction, story outlining, chapter writing), and build the Compose UI screens.

The app itself uses the Gemini API to generate stories at runtime, since that's what was available for this build, but the app's code, architecture, and pipeline logic were built with Codex from the ground up.

Codex feedback session ID: [PUT YOUR SESSION ID HERE]

## Getting your own Gemini API key

1. Go to Google AI Studio (aistudio.google.com)
2. Sign in with a Google account
3. Create a free API key
4. Open Pluck, go to Settings, and paste the key in

The free tier is enough to run and test the app. No payment info is needed.

## Running the project

**What you need first**
- Android Studio, a recent version
- An Android phone or emulator running Android 8.0 or newer
- A free Gemini API key (see above)

**Steps**
1. Clone or download this repository
2. Open the project folder in Android Studio
3. Let Gradle finish syncing (this happens automatically on first open)
4. Plug in a phone with USB debugging on, or start an emulator
5. Press Run in Android Studio
6. Once the app opens, go to Settings and paste in your Gemini API key
7. Go to the capture screen and take a few photos of different spots around you
8. Tap Finish Journey, then Generate Story
9. Wait while it works through the three steps, then read your story

**If you don't want to take real photos to test it**
There's a folder called `sample_data` in this repo with a few sample photos already in journey order. You can load these from the library screen using the "Load Sample Journey" option instead of using the camera, if you just want to see a story get generated without walking around.

## A few things worth knowing

- Nothing is uploaded anywhere except to Gemini's API, for generating the scene descriptions and story text. Your photos stay stored locally on your device.
- Story generation takes a little while, usually under a minute for a short journey, since it's three separate AI calls per photo plus one planning call for the whole journey.
- If a photo is too unclear for the vision model to make sense of, the app will still generate a scene for it using a generic fallback, so the story doesn't break.
- Every journey can only be turned into one story right now. Regenerating a new story from the same journey is on the roadmap, not built yet.

## What's not built yet, but planned

- An on-device mode using a local vision-language model, so the app can work fully offline with no API key needed at all
- The option to regenerate a different story from the same journey
- Location tagging using GPS instead of relying only on what's visible in the photo
- Exporting a finished story as a small illustrated PDF or shareable image set

## License

Not decided yet. Treat this as source-available for now; don't redistribute without asking.

---
Project by Hariom Sharnam
