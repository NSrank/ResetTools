package ns.toolboxes.resettools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /resettools 管理命令。
 */
public final class ResetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reload", "list", "run", "next", "help");

    private final ResetTools plugin;

    public ResetCommand(ResetTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Text.color("&aResetTools 配置已重载，当前任务数: "
                        + plugin.getTaskManager().getTasks().size()));
            }
            case "list" -> list(sender);
            case "next" -> next(sender);
            case "run" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.color("&c用法: /" + label + " run <任务id>"));
                    return true;
                }
                Task task = plugin.getTaskManager().getTask(args[1]);
                if (task == null) {
                    sender.sendMessage(Text.color("&c找不到任务: " + args[1]));
                    return true;
                }
                if (plugin.getTaskManager().runNow(task)) {
                    sender.sendMessage(Text.color("&a已手动触发任务 " + task.id()));
                } else {
                    sender.sendMessage(Text.color("&c任务 " + task.id() + " 的目标世界不存在: " + task.worldName()));
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.color("&e&lResetTools &7定时清空世界 / 重置世界"));
        sender.sendMessage(Text.color("&f/" + label + " list &7- 列出所有任务"));
        sender.sendMessage(Text.color("&f/" + label + " next &7- 查看下次执行时间"));
        sender.sendMessage(Text.color("&f/" + label + " run <id> &7- 立即执行任务"));
        sender.sendMessage(Text.color("&f/" + label + " reload &7- 重载 task.yml"));
    }

    private void list(CommandSender sender) {
        List<Task> tasks = new ArrayList<>(plugin.getTaskManager().getTasks());
        if (tasks.isEmpty()) {
            sender.sendMessage(Text.color("&7当前没有任务，请在 task.yml 中配置"));
            return;
        }
        sender.sendMessage(Text.color("&e任务列表 (&f" + tasks.size() + "&e)"));
        for (Task task : tasks) {
            String state = task.enabled() ? "&a启用" : "&c禁用";
            sender.sendMessage(Text.color("&8- &f" + task.id()
                    + " &7世界=&f" + task.worldName()
                    + " &7周=" + task.weekdays()
                    + " &7时间=&f" + task.time()
                    + " &7命令=" + task.commands().size()
                    + " &7延迟命令=" + task.delayCommands().size()
                    + " &7间隔=&f" + task.commandInterval() + "s"
                    + " &7状态=" + state));
        }
    }

    private void next(CommandSender sender) {
        LocalDateTime now = LocalDateTime.now();
        for (Task task : plugin.getTaskManager().getTasks()) {
            if (!task.enabled()) {
                sender.sendMessage(Text.color("&8" + task.id() + " &7(已禁用)"));
                continue;
            }
            LocalDateTime next = plugin.getTaskManager().nextExecution(task, now);
            sender.sendMessage(Text.color("&f" + task.id() + " &7-> &a" + (next == null ? "无" : next)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("run")) {
            List<String> result = new ArrayList<>();
            for (Task task : plugin.getTaskManager().getTasks()) {
                if (task.id().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(task.id());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
