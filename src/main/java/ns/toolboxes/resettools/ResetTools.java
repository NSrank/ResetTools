package ns.toolboxes.resettools;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * ResetTools 主类：定时把玩家传送出指定世界，然后重置该世界或执行命令。
 */
public final class ResetTools extends JavaPlugin {

    private TaskManager taskManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("无法创建插件数据目录: " + getDataFolder().getAbsolutePath());
        }
        if (!new File(getDataFolder(), "task.yml").exists()) {
            saveResource("task.yml", false);
        }

        this.taskManager = new TaskManager(this);
        this.taskManager.reload();

        PluginCommand command = getCommand("resettools");
        if (command != null) {
            ResetCommand executor = new ResetCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("命令 resettools 未在 plugin.yml 中注册");
        }

        getLogger().info("===================================");
        getLogger().info("ResetTools v1.0 已加载");
        getLogger().info("作者：NSrank & DeepSeek");
        getLogger().info("===================================");
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.cancelAll();
        }
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void reloadPlugin() {
        reloadConfig();
        taskManager.reload();
    }
}
