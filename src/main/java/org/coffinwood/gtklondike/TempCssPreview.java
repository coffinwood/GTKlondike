package org.coffinwood.gtklondike;

import org.coffinwood.gtklondike.ui.PileWidget;
import org.coffinwood.gtklondike.util.Utilities;
import org.gnome.gdk.Display;
import org.gnome.gio.ApplicationFlags;
import org.gnome.gtk.*;


/**
 * TEMPORARY - visually verifies the .card_pile_empty number centering outside the full game, by
 * showing a single pile-sized widget with the same CSS classes the real stock pile gets while
 * empty. Not part of the app; delete after use.
 */
public class TempCssPreview extends Application {
    public TempCssPreview() {
        setApplicationId("org.coffinwood.gtklondike.TempCssPreview");
        setFlags(ApplicationFlags.DEFAULT_FLAGS);
    }


    public static void main(String[] args) {
        Application app = new TempCssPreview();
        app.run(args);
        app.unref();
    }


    @Override
    public void activate() {
        CssProvider cssProvider = new CssProvider();
        cssProvider.loadFromString(Utilities.loadResourceText("/style/gtklondike.css"));
        Gtk.styleContextAddProviderForDisplay(Display.getDefault(), cssProvider, 800);

        // same rule GTKlondike.buildStockPileEmptyCss() generates, pinned to a representative
        // 2-digit number for this preview
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 180 75'>"
                + "<text x='90' y='37.5' text-anchor='middle' dominant-baseline='central' font-size='64' "
                + "font-family='sans-serif' font-weight='bold' fill='rgba(255,255,255,0.5)'>12</text></svg>";
        CssProvider dynamicProvider = new CssProvider();
        dynamicProvider.loadFromString(".card_pile_empty { background-image: url(\"data:image/svg+xml;utf8, " + svg + "\"); }");
        Gtk.styleContextAddProviderForDisplay(Display.getDefault(), dynamicProvider, 801);

        PileWidget stockLike = PileWidget.create();
        stockLike.addCssClass("card_pile_base");
        stockLike.addCssClass("card_pile_empty");
        stockLike.setSizeRequest(150, 210);

        Box wrapper = Box.builder().setHalign(Align.CENTER).setValign(Align.CENTER).build();
        wrapper.setMarginTop(40);
        wrapper.setMarginBottom(40);
        wrapper.setMarginStart(40);
        wrapper.setMarginEnd(40);
        wrapper.append(stockLike);

        ApplicationWindow window = new ApplicationWindow(this);
        window.setTitle("card_pile_empty preview");
        window.setChild(wrapper);
        window.setDefaultSize(300, 350);
        window.present();
    }
}
