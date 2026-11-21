import ns.toolboxes.resettools.Task;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class TaskParseTest {

    public static void main(String[] args) {
        int failures = 0;
        for (String path : args) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(path));
            int interval = config.getInt("settings.command-interval", 3);
            System.out.println("=== " + path + " keys=" + config.getKeys(false));
            for (String id : config.getKeys(false)) {
                if ("settings".equalsIgnoreCase(id)) {
                    System.out.println("settings.command-interval=" + interval
                            + " notify=" + config.getBoolean("settings.notify-player"));
                    continue;
                }
                ConfigurationSection section = config.getConfigurationSection(id);
                if (section == null) {
                    System.out.println(id + " -> 不是配置节，跳过");
                    continue;
                }
                try {
                    Task task = Task.parse(id, section, interval);
                    System.out.println(id
                            + " enabled=" + task.enabled()
                            + " days=" + task.weekdays()
                            + " time=" + task.time()
                            + " world=" + task.worldName()
                            + " tpWorld=" + task.teleportWorld()
                            + " explicitLoc=" + task.explicitLocation()
                            + " delayTicks=" + task.delayTicks()
                            + " delayCmds=" + task.delayCommands()
                            + " cmds=" + task.commands()
                            + " interval=" + task.commandInterval()
                            + " warnings=" + task.warnings());
                } catch (RuntimeException e) {
                    failures++;
                    System.out.println(id + " -> 解析失败: " + e.getMessage());
                }
            }
        }
        System.out.println("FAILURES=" + failures);
    }
}
