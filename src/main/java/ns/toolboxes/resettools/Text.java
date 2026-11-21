package ns.toolboxes.resettools;

import org.bukkit.ChatColor;

/**
 * 文本与占位符工具。
 */
public final class Text {

    private Text() {
    }

    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String fill(String template, String world, String task, String time, int seconds, int players) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{world}", world == null ? "" : world)
                .replace("{task}", task == null ? "" : task)
                .replace("{time}", time == null ? "" : time)
                .replace("{seconds}", Integer.toString(seconds))
                .replace("{players}", Integer.toString(players));
    }
}
