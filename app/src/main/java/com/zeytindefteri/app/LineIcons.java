package com.zeytindefteri.app;

/** OperationKinds ikon anahtarlarını çizgi ikon drawable'larına, tonları palet renklerine çevirir. */
public final class LineIcons {
    private LineIcons() {}

    public static int drawable(String key) {
        if (key == null) return R.drawable.ic_op_note;
        switch (key) {
            case "add": return R.drawable.ic_op_add;
            case "water": return R.drawable.ic_op_water;
            case "leaf": return R.drawable.ic_op_leaf;
            case "flask": return R.drawable.ic_op_flask;
            case "basket": return R.drawable.ic_op_basket;
            case "elek": return R.drawable.ic_op_elek;
            case "tag": return R.drawable.ic_op_tag;
            case "scissors": return R.drawable.ic_op_scissors;
            case "trap": return R.drawable.ic_op_trap;
            case "soil": return R.drawable.ic_op_soil;
            case "worker": return R.drawable.ic_op_worker;
            case "fuel": return R.drawable.ic_op_fuel;
            case "truck": return R.drawable.ic_op_truck;
            case "more": return R.drawable.ic_op_more;
            default: return R.drawable.ic_op_note;
        }
    }

    public static int forType(String type) {
        return drawable(OperationKinds.iconKey(type));
    }

    public static int toneColor(String type) {
        switch (OperationKinds.tone(type)) {
            case WATER: return GreenTheme.color(R.color.zd_water);
            case GREEN: return GreenTheme.color(R.color.zd_green);
            case AMBER: return GreenTheme.color(R.color.zd_warning);
            case SOIL: return GreenTheme.color(R.color.zd_soil);
            default: return GreenTheme.color(R.color.zd_text_soft);
        }
    }
}
