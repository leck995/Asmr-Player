package cn.tealc.asmr.model.galgame;

import java.util.List;

/**
 * Galgame 内置皮肤注册表。
 */
public final class GalgameSkinRegistry {
    private static final List<GalgameSkin> SKINS = List.of(
            new GalgameSkin(1, "经典", "Leck995", "最初的皮肤",
                    "/cn/tealc/asmr/ui/galgame/skins/classic",
                    "/cn/tealc/asmr/ui/galgame/skins/classic/galgame.css",
                    "/cn/tealc/asmr/ui/galgame/skins/classic/galgame.fxml"),
            new GalgameSkin(2, "简约", "Leck995", "小清新的Galgame皮肤",
                    "/cn/tealc/asmr/ui/galgame/skins/simple",
                    "/cn/tealc/asmr/ui/galgame/skins/simple/galgame.css",
                    "/cn/tealc/asmr/ui/galgame/skins/simple/galgame.fxml")
    );

    private GalgameSkinRegistry() {
    }

    public static List<GalgameSkin> all() {
        return SKINS;
    }

    public static GalgameSkin defaultSkin() {
        return SKINS.get(1);
    }

    public static GalgameSkin normalize(GalgameSkin skin) {
        if (skin != null) {
            for (GalgameSkin candidate : SKINS) {
                if (candidate.getFxml().equals(skin.getFxml()) || candidate.getCss().equals(skin.getCss())) {
                    return candidate;
                }
            }
            for (GalgameSkin candidate : SKINS) {
                if (candidate.getId() == skin.getId()) {
                    return candidate;
                }
            }
        }
        return defaultSkin();
    }
}
