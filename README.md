# GLMap Android demo

One compact Kotlin app demonstrates the GLMap 2.2.0 API. The catalog mirrors the iOS demo and groups each example by feature: map display, camera, draw objects, vector data, search, routing, and offline data.

The **Demo Mode** button above the catalog runs the same automatic SDK tour as the iOS app. The individual catalog screens remain the compact API examples to learn from.

## Run

1. Create an API key at <https://user.globus.software/apps/>.
2. Replace `YOUR_API_KEY` in `kotlinDemo/src/main/res/values/strings.xml` locally. Do not commit a real key.
3. Open this directory in Android Studio and run `kotlinDemo`.

To build against the published SDK 2.2.0 (no parent SDK checkout is required):

```shell
./gradlew :kotlinDemo:assembleDebug
```

To build against the GLMap sources in the parent repository:

```shell
./gradlew :kotlinDemo:assembleDebug -PuseLocalGLMap=true
```

Without `useLocalGLMap`, Gradle resolves the published `globus:glmap`, `globus:glsearch`, and `globus:glroute` artifacts for the version declared in `build.gradle`.

The demo targets Android SDK 37. Most screens construct their UI in Kotlin so the GLMap calls stay visible in one short feature file under `kotlinDemo/src/main/java/globus/demo`.

## SDK 2.2.0 API notes

- `DemoApp` calls `GLMapManager.Initialize` before any native SDK API. This initializes Core, Map, Search, and Route together; no separate `GLSearch.Initialize` call is needed. Its Boolean result reports initialization, not API-key validity.
- Hit-testing and draw-attribute queries use a captured `GLMapViewState`, closed with Kotlin `use` after each query.
- `GLMapValue.getSpanned` clones the supplied span objects. Search results use `SearchColorSpan`, a `Cloneable` subclass of `ForegroundColorSpan`; passing an ordinary `ForegroundColorSpan` crashes in the native bridge.
- `GeoJSONActivity` handles the `UpdateResult` passed to `setVectorObjects`: `Ready`, `Superseded`, `Cancelled`, or `Failed`. Updates and completions run on the main thread; `Ready` means geometry is ready to draw, not that a frame has been presented. Other examples pass `null` when no completion is needed.

Run the offline-search and GeoJSON update/recreation checks with an emulator or device connected:

```shell
./gradlew :kotlinDemo:connectedDebugAndroidTest
```

API documentation: <https://globus.software/docs>
