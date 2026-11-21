package ns.toolboxes.resettools;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个重置任务，由 task.yml 中的一个顶级配置节解析而来。
 */
public final class Task {

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    /**
     * 执行前的广播提醒。
     *
     * @param seconds 提前多少秒
     * @param message 提示内容
     */
    public record Warning(int seconds, String message) {
    }

    private final String id;
    private final boolean enabled;
    private final Set<Integer> weekdays;
    private final LocalTime time;
    private final String worldName;
    private final boolean teleportEnabled;
    private final String teleportWorld;
    private final boolean explicitLocation;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String teleportMessage;
    private final int commandInterval;
    private final List<String> commands;
    private final List<String> delayCommands;
    private final long delayTicks;
    private final List<Warning> warnings;

    private Task(String id, boolean enabled, Set<Integer> weekdays, LocalTime time, String worldName,
                 boolean teleportEnabled, String teleportWorld, boolean explicitLocation,
                 double x, double y, double z, float yaw, float pitch, String teleportMessage,
                 int commandInterval, List<String> commands, List<String> delayCommands,
                 long delayTicks, List<Warning> warnings) {
        this.id = id;
        this.enabled = enabled;
        this.weekdays = weekdays;
        this.time = time;
        this.worldName = worldName;
        this.teleportEnabled = teleportEnabled;
        this.teleportWorld = teleportWorld;
        this.explicitLocation = explicitLocation;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.teleportMessage = teleportMessage;
        this.commandInterval = commandInterval;
        this.commands = Collections.unmodifiableList(commands);
        this.delayCommands = Collections.unmodifiableList(delayCommands);
        this.delayTicks = delayTicks;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public Set<Integer> weekdays() {
        return weekdays;
    }

    public LocalTime time() {
        return time;
    }

    public String worldName() {
        return worldName;
    }

    public boolean teleportEnabled() {
        return teleportEnabled;
    }

    public String teleportWorld() {
        return teleportWorld;
    }

    public boolean explicitLocation() {
        return explicitLocation;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public String teleportMessage() {
        return teleportMessage;
    }

    public int commandInterval() {
        return commandInterval;
    }

    public List<String> commands() {
        return commands;
    }

    /**
     * command 全部执行完毕后，再等待 {@link #delayTicks()} 才执行的命令。
     */
    public List<String> delayCommands() {
        return delayCommands;
    }

    public long delayTicks() {
        return delayTicks;
    }

    public List<Warning> warnings() {
        return warnings;
    }

    /**
     * 解析单个任务。配置不合法时抛出 {@link IllegalArgumentException}，由调用方记录并跳过该任务。
     */
    public static Task parse(String id, ConfigurationSection section, int defaultCommandInterval) {
        boolean enabled = section.getBoolean("enabled", true);
        Set<Integer> weekdays = parseWeekdays(section);
        LocalTime time = parseTime(section.get("time"));

        String worldName = firstNonBlank(section.getString("worldid"), section.getString("world"));
        if (worldName == null) {
            throw new IllegalArgumentException("缺少 worldid");
        }

        boolean teleportEnabled = true;
        String teleportWorld = null;
        boolean explicitLocation = false;
        double x = 0.0D;
        double y = 64.0D;
        double z = 0.0D;
        float yaw = 0.0F;
        float pitch = 0.0F;
        String teleportMessage = null;

        Object rawTeleport = section.get("teleport");
        if (rawTeleport instanceof ConfigurationSection teleport) {
            teleportEnabled = teleport.getBoolean("enabled", true);
            teleportWorld = firstNonBlank(teleport.getString("world"));
            explicitLocation = teleport.isSet("x") || teleport.isSet("y") || teleport.isSet("z");
            x = teleport.getDouble("x", 0.5D);
            y = teleport.getDouble("y", 64.0D);
            z = teleport.getDouble("z", 0.5D);
            yaw = (float) teleport.getDouble("yaw", 0.0D);
            pitch = (float) teleport.getDouble("pitch", 0.0D);
            teleportMessage = firstNonBlank(teleport.getString("message"));
        } else if (rawTeleport instanceof Boolean flag) {
            teleportEnabled = flag;
        } else if (rawTeleport instanceof String destination) {
            teleportWorld = firstNonBlank(destination);
        }

        int commandInterval = Math.max(0, section.getInt("command-interval", defaultCommandInterval));
        List<String> commands = parseCommands(section, "command");

        List<String> delayCommands = new ArrayList<>();
        long delayTicks = 0L;
        Object rawDelay = section.get("delay-command");
        if (rawDelay instanceof ConfigurationSection delaySection) {
            delayTicks = parseDelayTicks(delaySection.get("time"));
            delayCommands = parseCommands(delaySection, "command");
        }

        List<Warning> warnings = parseWarnings(section);

        return new Task(id, enabled, weekdays, time, worldName, teleportEnabled, teleportWorld,
                explicitLocation, x, y, z, yaw, pitch, teleportMessage, commandInterval, commands,
                delayCommands, delayTicks, warnings);
    }

    private static Set<Integer> parseWeekdays(ConfigurationSection section) {
        Object raw = section.get("weekdays");
        if (raw == null) {
            raw = section.get("weekday");
        }
        if (raw == null) {
            throw new IllegalArgumentException("缺少 weekday（1-7，或列表）");
        }
        List<Object> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            values.addAll(list);
        } else {
            values.add(raw);
        }
        Set<Integer> weekdays = new TreeSet<>();
        for (Object value : values) {
            int day = toWeekday(value);
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("weekday 必须是 1-7，实际为: " + value);
            }
            weekdays.add(day);
        }
        if (weekdays.isEmpty()) {
            throw new IllegalArgumentException("weekday 不能为空");
        }
        return Collections.unmodifiableSet(weekdays);
    }

    private static int toWeekday(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        switch (text) {
            case "mon", "monday", "周一":
                return 1;
            case "tue", "tuesday", "周二":
                return 2;
            case "wed", "wednesday", "周三":
                return 3;
            case "thu", "thursday", "周四":
                return 4;
            case "fri", "friday", "周五":
                return 5;
            case "sat", "saturday", "周六":
                return 6;
            case "sun", "sunday", "周日", "周天":
                return 7;
            default:
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("无法识别的 weekday: " + value);
                }
        }
    }

    private static LocalTime parseTime(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("缺少 time（HH:mm）");
        }
        if (raw instanceof Number number) {
            int value = number.intValue();
            try {
                if (value >= 0 && value <= 23) {
                    return LocalTime.of(value, 0);
                }
                // YAML 1.1 会把不加引号的 4:00 解析成六十进制整数 240（4*60+0）
                return LocalTime.of(value / 60, value % 60);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("time 不合法: " + raw + "（建议写成 \"HH:mm\" 并加引号）");
            }
        }
        String text = String.valueOf(raw).trim().replace('：', ':');
        String[] parts = text.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("time 必须为 HH:mm 格式: " + raw);
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            int second = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return LocalTime.of(hour, minute, second);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("time 不合法: " + raw);
        }
    }

    /**
     * 解析 "5m" / "30s" / "2h" / "1d" / "90" 这样的延迟，返回 ticks。
     */
    private static long parseDelayTicks(Object raw) {
        if (raw == null) {
            return 0L;
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return 0L;
        }
        double multiplier = 1000.0D;
        char unit = text.charAt(text.length() - 1);
        if (unit == 's' || unit == 'm' || unit == 'h' || unit == 'd') {
            text = text.substring(0, text.length() - 1).trim();
            switch (unit) {
                case 'm' -> multiplier = 60_000.0D;
                case 'h' -> multiplier = 3_600_000.0D;
                case 'd' -> multiplier = 86_400_000.0D;
                default -> multiplier = 1_000.0D;
            }
        }
        try {
            double millis = Double.parseDouble(text) * multiplier;
            if (millis < 0) {
                throw new IllegalArgumentException("delay-command.time 不能为负数: " + raw);
            }
            return Math.max(0L, (long) (millis / 50.0D));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("delay-command.time 不合法（如 30s / 5m / 2h / 1d）: " + raw);
        }
    }

    private static List<String> parseCommands(ConfigurationSection section, String key) {
        List<String> commands = new ArrayList<>();
        Object raw = section.get(key);
        if (raw instanceof ConfigurationSection commandsSection) {
            List<String> keys = new ArrayList<>(commandsSection.getKeys(false));
            keys.sort(Comparator.comparingInt(Task::keyIndex));
            for (String childKey : keys) {
                String command = commandsSection.getString(childKey);
                if (command != null && !command.isBlank()) {
                    commands.add(command.trim());
                }
            }
        } else if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    commands.add(String.valueOf(item).trim());
                }
            }
        }
        return commands;
    }

    private static int keyIndex(String key) {
        Matcher matcher = NUMBER.matcher(key);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static List<Warning> parseWarnings(ConfigurationSection section) {
        List<Warning> warnings = new ArrayList<>();
        Object raw = section.get("warnings");
        if (raw instanceof ConfigurationSection warningsSection) {
            for (String key : warningsSection.getKeys(false)) {
                int seconds;
                try {
                    seconds = Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String message = warningsSection.getString(key, "");
                warnings.add(new Warning(seconds, message));
            }
            warnings.sort(Comparator.comparingInt(Warning::seconds).reversed());
        }
        return warnings;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
