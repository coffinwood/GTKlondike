# GTKlondike

A Klondike Solitaire game for the Linux desktop, built with [GTK4](https://www.gtk.org/) and Java, using [java-gi](https://www.java-gi.org/) for the GTK bindings.

This project started partly as a proof of concept: how far can you get building a real, native-feeling GTK4 desktop application in Java, using GTK's modern Java bindings rather than a web-view wrapper or a JVM-native toolkit like Swing/JavaFX? If you're exploring `java-gi` or Java+GTK yourself, the source is meant to be a readable, real-world reference rather than a toy example.

!Important note: this is neither a work in progress nor will the software be supported in any way.!

<img width="1600" height="1070" alt="Screenshot of GTKlondike" src="https://github.com/user-attachments/assets/c775bd33-7798-46e9-b391-4261ced9f523" />

## Features

- Classic Klondike Solitaire: 7 tableau lanes, 4 foundations, stock/waste, draw-1 or draw-3.
- Drag-and-drop moves, click-to-move, undo, and one-click auto-complete once the rest of the game is forced.
- A custom `PileLayoutManager` (a real GTK `LayoutManager`) for the cascading/fanned card layout, rather than absolute positioning.
- Preferences: card scale, card back texture, background theme, draw amount, and an optional elapsed-time timer — persisted via GSettings.
- A frosted-glass victory banner and pause overlay built from GTK4's `Overlay` + CSS.

## Requirements

- **JDK 22 or newer** (developed/tested against JDK 26) — the app relies on the Java Foreign Function & Memory API that `java-gi`'s bindings are built on.
- **GTK4** and **glib2** development tools (specifically `glib-compile-schemas`, used at build time to compile the app's GSettings schema) installed on your system. This is a Linux desktop application; it hasn't been tested on macOS or Windows.

## Building and running

```sh
./gradlew shadowJar
./run-gtklondike.sh
```

`run-gtklondike.sh` runs the packaged fat jar (`build/libs/GTKlondike-all.jar`) the way a desktop launcher would, with a couple of Linux/NVIDIA-specific workarounds baked in (see the comments at the top of the script). To run it directly instead:

```sh
java --enable-native-access=ALL-UNNAMED -jar build/libs/GTKlondike-all.jar
```

<img width="1600" height="1070" alt="Screenshot of GTKlondike" src="https://github.com/user-attachments/assets/dd923921-1bf8-4a53-8308-f64b77bd0909" />

## License

MIT — see [LICENSE](LICENSE).

## Credits

GTKlondike bundles or depends on several third-party works; see [`src/main/resources/attributions.xml`](src/main/resources/attributions.xml) for the full, machine-readable list (also shown in-app via the About dialog). In short:

- [java-gi](https://github.com/jwharm/java-gi) (Jan-Willem Harmannij) — the GTK4 Java bindings this app is built on, statically bundled under LGPL-2.1.
- [Gradle Shadow Plugin](https://plugins.gradle.org/plugin/com.gradleup.shadow) — fat-jar packaging.
- [Card art](https://opengameart.org/content/bridge-sized-playing-card-deck-png-cc0) by Mesmedir (CC0).
- [Suit symbols](https://pixabay.com/vectors/diamonds-heart-pik-cross-cards-335025/) by stux, adapted.
- Background patterns by [ComeOnCreative](https://codepen.io/ComeonCreative/pen/BWwXLG), [Manuel Pinto](https://codepen.io/P1N2O/pen/pyBNzX), [Arman Borkhani](https://codepen.io/armanb/pen/pvzYjaQ), [Steve Schoger](https://heropatterns.com/), and [csemszepp](https://uiverse.io/csemszepp/neat-parrot-45).
- "Super Bouncer" font by fsuarez913.

<img width="506" height="690" alt="Screenshot of GTKlondike's preferences dialogue" src="https://github.com/user-attachments/assets/53e72cc8-ee14-4479-89ad-bace75716217" />

## Development notes

This project was built collaboratively with [Claude](https://www.anthropic.com/claude) (Anthropic's AI assistant) as a development partner — including implementation, debugging (notably a set of gnarly native-interop reference-counting bugs in the GTK/java-gi FFI layer), and refactoring. It's shared here in that spirit, as much as a proof of concept for AI-assisted development of a real native GTK application as for the Java+GTK combination itself.
