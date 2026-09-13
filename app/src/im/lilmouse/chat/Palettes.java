package im.lilmouse.chat;

/**
 * Material 3 色板。
 *
 * 色值取自 RikkaHub（MIT License, https://github.com/rikkahub/rikkahub）
 * 的 ui/theme/presets/*.kt，为标准 Material Theme Builder 生成的完整 M3 令牌。
 *
 * 本文件由脚本生成，请勿手改。配色在「设置 → 外观 → 配色方案」切换。
 * 索引与 UI.TK_* 常量一一对应；索引 0（跟随系统）走动态取色，不使用本表。
 */
public final class Palettes {
    private Palettes() {}

    public static final String[] IDS   = {"system", "minimal", "claude", "autumn", "ocean", "sakura", "spring", "black"};
    public static final String[] NAMES = {"跟随系统", "极简", "Claude", "秋日", "海洋", "樱花", "春意", "纯黑"};
    /** 上游预设名，便于回溯 */
    public static final String[] SOURCE = {"", "Minimal", "Claude", "Autumn", "Ocean", "Sakura", "Spring", "Black"};

    /** 令牌名（仅供阅读，实际索引见 UI.TK_*） */
    public static final String[] TOKEN = {"primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "secondaryContainer", "onSecondaryContainer", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "outline", "outlineVariant", "inverseSurface", "inverseOnSurface", "error"};

    /**
     * [paletteIndex][mode][tokenIndex] -> "RRGGBB"
     *   mode: 0 = 浅色(Light)，1 = 深色(Dark)
     *   paletteIndex 与 IDS 对应；索引 0 为占位（跟随系统，运行时取动态色）
     */
    public static final String[][][] C = {
        null, // 0 跟随系统：运行时动态取色
        { // 1 极简 (Minimal)
            { "2563EB", "FFFFFF", "E7F0FE", "1B4ACB", "EFF0F2", "3B3E44", "FFFFFF", "16181D", "F1F2F4", "6A6E75", "FFFFFF", "FAFAFB", "F6F7F8", "F1F2F4", "EAEBEE", "C7CACF", "E8E9EC", "2A2C31", "F3F4F6", "D93A32" },
            { "8CB0FF", "082C6E", "1D3E7F", "D9E4FF", "3A3D43", "E2E4E8", "0F1012", "E6E7EA", "2B2D31", "A0A4AB", "0A0B0C", "151619", "1A1B1E", "222327", "2A2C30", "4C4F55", "2E3035", "E6E7EA", "2A2C31", "FFB4AB" },
        },
        { // 2 Claude (Claude)
            { "C96442", "FFFFFF", "F7E3D9", "8A3E23", "E9E6DC", "3D3929", "FAF9F5", "262624", "EDEAE0", "6F675C", "FFFFFF", "F7F5EF", "F2F0E8", "EDEAE0", "E7E3D8", "BDB7A9", "E5E1D6", "33312C", "F7F5EF", "B03D2E" },
            { "E4906E", "4A1B0B", "7A3620", "FFDBCE", "4A453A", "EDE7DA", "1F1E1D", "EDEAE3", "3A372F", "CBC5B8", "171614", "262624", "2B2A27", "343230", "3E3C37", "5A564C", "3A372F", "EDEAE3", "33312C", "FFB4A6" },
        },
        { // 3 秋日 (Autumn)
            { "735C0C", "FFFFFF", "FFE08B", "584400", "F2E1BB", "50462A", "FFF8F1", "1F1B13", "EBE1CF", "4C4639", "FFFFFF", "FBF3E5", "F5EDDF", "F0E7D9", "EAE1D4", "7E7667", "CFC6B4", "343027", "F8F0E2", "BA1A1A" },
            { "E3C46D", "3D2F00", "584400", "FFE08B", "50462A", "F2E1BB", "16130B", "EAE1D4", "4C4639", "CFC6B4", "110E07", "1F1B13", "231F17", "2D2A21", "38342B", "989080", "4C4639", "EAE1D4", "343027", "FFB4AB" },
        },
        { // 4 海洋 (Ocean)
            { "116682", "FFFFFF", "BDE9FF", "004D64", "D0E6F2", "354A53", "F6FAFD", "171C1F", "DCE4E9", "40484C", "FFFFFF", "F0F4F8", "EAEEF2", "E4E9EC", "DFE3E7", "70787D", "C0C8CD", "2C3134", "EDF1F5", "BA1A1A" },
            { "8BD0EF", "003546", "004D64", "BDE9FF", "354A53", "D0E6F2", "0F1417", "DFE3E7", "40484C", "C0C8CD", "0A0F11", "171C1F", "1B2023", "262B2D", "303538", "8A9297", "40484C", "DFE3E7", "2C3134", "FFB4AB" },
        },
        { // 5 樱花 (Sakura)
            { "8E4955", "FFFFFF", "FFD9DD", "72333E", "FFD9DD", "5C3F43", "FFF8F7", "22191A", "F3DDDF", "524345", "FFFFFF", "FFF0F1", "FBEAEB", "F6E4E5", "F0DEDF", "847374", "D7C1C3", "382E2F", "FEEDED", "BA1A1A" },
            { "FFB2BC", "561D28", "72333E", "FFD9DD", "5C3F43", "FFD9DD", "1A1112", "F0DEDF", "524345", "D7C1C3", "140C0D", "22191A", "261D1E", "312828", "3D3233", "9F8C8E", "524345", "F0DEDF", "382E2F", "FFB4AB" },
        },
        { // 6 春意 (Spring)
            { "4C662B", "FFFFFF", "CDEDA3", "354E16", "DCE7C8", "404A33", "F9FAEF", "1A1C16", "E1E4D5", "44483D", "FFFFFF", "F3F4E9", "EEEFE3", "E8E9DE", "E2E3D8", "75796C", "C5C8BA", "2F312A", "F1F2E6", "BA1A1A" },
            { "B1D18A", "1F3701", "354E16", "CDEDA3", "404A33", "DCE7C8", "12140E", "E2E3D8", "44483D", "C5C8BA", "0C0F09", "1A1C16", "1E201A", "282B24", "33362E", "8F9285", "44483D", "E2E3D8", "2F312A", "FFB4AB" },
        },
        { // 7 纯黑 (Black)
            { "606060", "FFFCFC", "E6E6E6", "424242", "F3F3F3", "575757", "FFFFFF", "252525", "F7F7F7", "444444", "FFFFFF", "F8F8F8", "F7F7F7", "EBEBEB", "E8E8E8", "B5B5B5", "EBEBEB", "343434", "EEEEF0", "DC2626" },
            { "EBEBEB", "343434", "3B3B3B", "B5B5B5", "444444", "FCFCFC", "1C1C1C", "FCFCFC", "444444", "B5B5B5", "1A1A1A", "252525", "2A2A2A", "343434", "3F3F3F", "8E8E8E", "444444", "FCFCFC", "343434", "EF4444" },
        },
    };
}
