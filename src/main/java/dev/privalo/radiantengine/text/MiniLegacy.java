package dev.privalo.radiantengine.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the MiniMessage subset used by RadiantEngine into legacy section-sign text for servers without
 * Adventure (Spigot). Supports named and hex colors, gradients, decorations, reset and new lines; interactive tags
 * (click, hover...) are stripped.
 */
public final class MiniLegacy {

    private static final char S = '§';
    private static final Map<String, Character> COLORS = new HashMap<>();
    private static final Map<String, Character> DECORATIONS = new HashMap<>();

    static {
        COLORS.put("black", '0');
        COLORS.put("dark_blue", '1');
        COLORS.put("dark_green", '2');
        COLORS.put("dark_aqua", '3');
        COLORS.put("dark_red", '4');
        COLORS.put("dark_purple", '5');
        COLORS.put("gold", '6');
        COLORS.put("gray", '7');
        COLORS.put("grey", '7');
        COLORS.put("dark_gray", '8');
        COLORS.put("dark_grey", '8');
        COLORS.put("blue", '9');
        COLORS.put("green", 'a');
        COLORS.put("aqua", 'b');
        COLORS.put("red", 'c');
        COLORS.put("light_purple", 'd');
        COLORS.put("yellow", 'e');
        COLORS.put("white", 'f');
        DECORATIONS.put("bold", 'l');
        DECORATIONS.put("b", 'l');
        DECORATIONS.put("italic", 'o');
        DECORATIONS.put("i", 'o');
        DECORATIONS.put("em", 'o');
        DECORATIONS.put("underlined", 'n');
        DECORATIONS.put("u", 'n');
        DECORATIONS.put("strikethrough", 'm');
        DECORATIONS.put("st", 'm');
        DECORATIONS.put("obfuscated", 'k');
        DECORATIONS.put("obf", 'k');
    }

    private record Tag(String name, String color, Character decoration) {
    }

    private MiniLegacy() {
    }

    public static String toLegacy(String mini) {
        StringBuilder out = new StringBuilder();
        List<Tag> stack = new ArrayList<>();
        int i = 0;
        int n = mini.length();
        while (i < n) {
            char c = mini.charAt(i);
            if (c == '\\' && i + 1 < n && mini.charAt(i + 1) == '<') {
                out.append('<');
                i += 2;
                continue;
            }
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            int end = mini.indexOf('>', i);
            if (end < 0) {
                out.append(mini, i, n);
                break;
            }
            String raw = mini.substring(i + 1, end);
            String lower = raw.toLowerCase(Locale.ROOT);
            i = end + 1;
            if (lower.startsWith("/")) {
                String name = lower.substring(1);
                for (int k = stack.size() - 1; k >= 0; k--) {
                    if (stack.get(k).name().equals(name) || name.isEmpty()) {
                        stack.remove(k);
                        break;
                    }
                }
                emitState(out, stack);
                continue;
            }
            if (lower.equals("reset") || lower.equals("r")) {
                stack.clear();
                out.append(S).append('r');
                continue;
            }
            if (lower.equals("newline") || lower.equals("br")) {
                out.append('\n');
                emitState(out, stack);
                continue;
            }
            if (lower.startsWith("gradient")) {
                int close = indexOfIgnoreCase(mini, "</gradient>", i);
                String inner = close < 0 ? mini.substring(i) : mini.substring(i, close);
                List<String> colors = new ArrayList<>();
                for (String part : raw.split(":")) {
                    String hex = hexOf(part);
                    if (hex != null) {
                        colors.add(hex);
                    }
                }
                out.append(gradient(inner, colors, stack));
                i = close < 0 ? n : close + "</gradient>".length();
                emitState(out, stack);
                continue;
            }
            String colorName = lower.startsWith("color:") || lower.startsWith("c:") ? lower.substring(lower.indexOf(':') + 1) : lower;
            String code = colorCode(colorName);
            if (code != null) {
                stack.add(new Tag(lower.startsWith("color") || lower.startsWith("c:") ? lower.substring(0, lower.indexOf(':')) : lower, code, null));
                emitState(out, stack);
                continue;
            }
            Character deco = DECORATIONS.get(lower);
            if (deco != null) {
                stack.add(new Tag(lower, null, deco));
                out.append(S).append(deco);
            }
            // unknown / interactive tags are dropped
        }
        return out.toString();
    }

    private static void emitState(StringBuilder out, List<Tag> stack) {
        out.append(S).append('r');
        String color = null;
        for (Tag t : stack) {
            if (t.color() != null) {
                color = t.color();
            }
        }
        if (color != null) {
            out.append(color);
        }
        for (Tag t : stack) {
            if (t.decoration() != null) {
                out.append(S).append(t.decoration());
            }
        }
    }

    private static String colorCode(String name) {
        Character legacy = COLORS.get(name);
        if (legacy != null) {
            return "" + S + legacy;
        }
        String hex = hexOf(name);
        return hex == null ? null : hexCode(hex);
    }

    private static String hexOf(String s) {
        String h = s.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        } else {
            Character legacy = COLORS.get(h.toLowerCase(Locale.ROOT));
            return legacy == null ? null : legacyToHex(legacy);
        }
        return h.matches("[0-9a-fA-F]{6}") ? h.toUpperCase(Locale.ROOT) : null;
    }

    private static String legacyToHex(char code) {
        return switch (code) {
            case '0' -> "000000";
            case '1' -> "0000AA";
            case '2' -> "00AA00";
            case '3' -> "00AAAA";
            case '4' -> "AA0000";
            case '5' -> "AA00AA";
            case '6' -> "FFAA00";
            case '7' -> "AAAAAA";
            case '8' -> "555555";
            case '9' -> "5555FF";
            case 'a' -> "55FF55";
            case 'b' -> "55FFFF";
            case 'c' -> "FF5555";
            case 'd' -> "FF55FF";
            case 'e' -> "FFFF55";
            default -> "FFFFFF";
        };
    }

    private static String hexCode(String hex) {
        StringBuilder sb = new StringBuilder().append(S).append('x');
        for (char ch : hex.toCharArray()) {
            sb.append(S).append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }

    private static String gradient(String inner, List<String> colors, List<Tag> outerStack) {
        if (colors.isEmpty()) {
            colors = List.of("FFFFFF", "FFFFFF");
        } else if (colors.size() == 1) {
            colors = List.of(colors.get(0), colors.get(0));
        }
        // Split inner text into visible characters, applying decoration tags encountered on the way.
        List<Character> chars = new ArrayList<>();
        List<String> decorationsPerChar = new ArrayList<>();
        StringBuilder decorations = new StringBuilder();
        for (Tag t : outerStack) {
            if (t.decoration() != null) {
                decorations.append(S).append(t.decoration());
            }
        }
        int i = 0;
        while (i < inner.length()) {
            char c = inner.charAt(i);
            if (c == '<') {
                int end = inner.indexOf('>', i);
                if (end > 0) {
                    String tag = inner.substring(i + 1, end).toLowerCase(Locale.ROOT);
                    if (tag.startsWith("/")) {
                        Character d = DECORATIONS.get(tag.substring(1));
                        if (d != null) {
                            String code = "" + S + d;
                            int idx = decorations.lastIndexOf(code);
                            if (idx >= 0) {
                                decorations.delete(idx, idx + 2);
                            }
                        }
                    } else {
                        Character d = DECORATIONS.get(tag);
                        if (d != null) {
                            decorations.append(S).append(d);
                        }
                    }
                    i = end + 1;
                    continue;
                }
            }
            chars.add(c);
            decorationsPerChar.add(decorations.toString());
            i++;
        }
        StringBuilder out = new StringBuilder();
        int total = chars.size();
        for (int k = 0; k < total; k++) {
            double t = total <= 1 ? 0 : (double) k / (total - 1);
            out.append(hexCode(interpolate(colors, t))).append(decorationsPerChar.get(k)).append(chars.get(k));
        }
        return out.toString();
    }

    private static String interpolate(List<String> colors, double t) {
        double scaled = t * (colors.size() - 1);
        int idx = Math.min(colors.size() - 2, (int) Math.floor(scaled));
        double local = scaled - idx;
        int a = Integer.parseInt(colors.get(idx), 16);
        int b = Integer.parseInt(colors.get(idx + 1), 16);
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * local);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * local);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * local);
        return String.format("%02X%02X%02X", r, g, bl);
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        return s.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }
}
