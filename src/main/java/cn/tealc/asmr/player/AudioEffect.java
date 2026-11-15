package cn.tealc.asmr.player;

/**
 * VLCJ 音效预设枚举（中英文对照版）
 */
public enum AudioEffect {
    FLAT("Flat", "默认", "所有频段均衡，无增强或衰减"),
    CLASSICAL("Classical", "古典", "平衡全频段，适合交响乐、钢琴曲等"),
    CLUB("Club", "俱乐部", "增强低音和环绕感，适合夜店风格音乐"),
    DANCE("Dance", "舞曲", "强化低音节奏，适合电子舞曲（EDM）"),
    FULL_BASS("Full bass", "超重低音", "极强低频，适合低音炮效果"),
    FULL_BASS_TREBLE("Full bass and treble", "高低音增强", "同时提升低频和高频，中频稍弱"),
    FULL_TREBLE("Full treble", "高音增强", "突出高频，适合人声或清脆乐器"),
    HEADPHONES("Headphones", "耳机优化", "优化立体声分离度，减少耳疲劳"),
    LARGE_HALL("Large Hall", "大厅", "模拟音乐厅混响效果，空间感更强"),
    LIVE("Live", "现场", "增强临场感，类似演唱会效果"),
    PARTY("Party", "派对", "均衡增强中低频，适合多人环境播放"),
    POP("Pop", "流行", "突出人声和中高频，适合流行音乐"),
    REGGAE("Reggae", "雷盖", "强化低音和节奏感，适合雷鬼风格"),
    ROCK("Rock", "摇滚", "增强低频和高频冲击力，适合摇滚乐"),
    SKA("Ska", "斯卡", "介于雷鬼和摇滚之间，中频稍突出"),
    SOFT("Soft", "柔和", "衰减高低频，中频柔和，适合轻音乐"),
    SOFT_ROCK("Soft rock", "慢摇", "比标准摇滚更温和，减少高频刺耳感"),
    TECHNO("Techno", "电子", "强调电子合成器的低频和中高频");
    //DIY("Diy", "自定义", "自定义效果");

    private final String presetName;  // VLCJ 原始预设名称
    private final String chineseName; // 中文名称
    private final String description; // 效果描述

    AudioEffect(String presetName, String chineseName, String description) {
        this.presetName = presetName;
        this.chineseName = chineseName;
        this.description = description;
    }

    // 获取 VLCJ 原始预设名称（与 Equalizer.Preset 对应）
    public String getPresetName() {
        return presetName;
    }

    // 获取中文名称
    public String getChineseName() {
        return chineseName;
    }

    // 获取效果描述
    public String getDescription() {
        return description;
    }


    @Override
    public String toString() {
        return chineseName;
    }
}