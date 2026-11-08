package com.extantape.voidwell.reward;

/** The rewards a full meter can offer. The id is what's saved. */
public enum Reward {
    FEATHERFALL("featherfall"),
    VOID_SAFE("void-safe"),
    BOUNTIFUL_BLOCK("bountiful-block"),
    VOID_GIVES_BACK("void-gives-back"),
    ISLAND_TRAVEL("island-travel");

    public final String id;

    Reward(String id) {
        this.id = id;
    }

    public static Reward byId(String id) {
        for (Reward r : values()) if (r.id.equals(id)) return r;
        return null;
    }
}
