# core:vybe — on-device natural-language music discovery (foundation)

Pure Kotlin, no Android or network dependencies:

- `TextEmbedder` — the only seam to a model runtime (text → vector, run on a background thread).
- `SongDescriber` — the text embedded for a song: title, artist, album, genre, year and a
  short excerpt of lyrics already stored in the library (LRC timing removed). Nothing is fetched.
- `cosineSimilarity`, `VybeRanker` — brute-force ranking, best first, ties by song id.
- `VybeSearch` — query + songs → ranked matches through a `TextEmbedder`.

Unit tests (`src/test`) use a deterministic fake embedder, so ranking is verified without a model.

## Real embedder: `core:vybe-mediapipe`

`MediaPipeTextEmbedder` runs **EmbeddingGemma 300m** on the device through the officially
documented MediaPipe Text Embedder task (`com.google.mediapipe:tasks-text:1.1.0`), with
L2-normalised output, and EmbeddingGemma's retrieval prompts applied by the library itself
(`embed(text, TextFormatContext)`). Every MediaPipe call was checked against the tasks-text 1.1.0
classes (javap). Sources:

- Android API: developers.google.com/edge/mediapipe/solutions/text/text_embedder/android
- Supported models: developers.google.com/edge/mediapipe/solutions/text/text_embedder
  ("Embedding Gemma", int4+int8, max 512 tokens; 200 ms CPU latency on a Galaxy S26 Ultra).
- Prompts / 768-dim output: ai.google.dev/gemma/docs/embeddinggemma/model_card

The model is a single bundle with its tokenizer, so no tensor handling is needed in empee3.
Inference is entirely local; the app never downloads or bundles the model.

## Supplying the model (manual, by the user)

1. Read and accept the Gemma Terms of Use: https://ai.google.dev/gemma/terms
2. Download the official bundle (~184 MB):
   https://storage.googleapis.com/mediapipe-models/text_embedder/embedding_gemma/int4int8/latest/embedding_gemma.task
3. For the on-device tests, copy it to the test app's files directory, e.g.
   `adb push embedding_gemma.task /sdcard/Android/data/com.lochanpthakke.empee3.core.vybe.mediapipe.test/files/`
   and run `./gradlew :core:vybe-mediapipe:connectedDebugAndroidTest`. Without the file these tests are skipped.
4. In the app (later phase), the user will pick the file once; it is copied to app-private
   storage and loaded with `MediaPipeTextEmbedder.fromFile(context, file)`.
