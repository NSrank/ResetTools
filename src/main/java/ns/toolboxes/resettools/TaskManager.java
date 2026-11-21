package ns.toolboxes.resettools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 task.yml、排程并执行任务。
 */
public final class TaskManager {

    private final ResetTools plugin;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final Map<String, BukkitTask> mainHandles = new LinkedHashMap<>();
    private final List<BukkitTask> scheduled = new ArrayList<>();

    private int commandInterval = 3;
    private boolean notifyPlayers = true;

    public TaskManager(ResetTools plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cancelAll();
        tasks.clear();

        File file = new File(plugin.getDataFolder(), "task.yml");
        if (!file.exists()) {
            plugin.saveResource("task.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        commandInterval = Math.max(0, config.getInt("settings.command-interval", 3));
        notifyPlayers = config.getBoolean("settings.notify-player", true);

        for (String id : config.getKeys(false)) {
            if ("settings".equalsIgnoreCase(id)) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                Task task = Task.parse(id, section, commandInterval);
                tasks.put(id, task);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("任务 " + id + " 配置有误，已跳过: " + e.getMessage());
            }
        }

        plugin.getLogger().info("已读取 " + tasks.size() + " 个任务（命令间隔 " + commandInterval + " 秒）");
        for (Task task : tasks.values()) {
            if (task.enabled()) {
                schedule(task);
            }
        }
    }

    public Collection<Task> getTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    public Task getTask(String id) {
        for (Task task : tasks.values()) {
            if (task.id().equalsIgnoreCase(id)) {
                return task;
            }
        }
        return null;
    }

    public void cancelAll() {
        List<BukkitTask> all = new ArrayList<>(mainHandles.values());
        all.addAll(scheduled);
        for (BukkitTask handle : all) {
            if (handle != null && !handle.isCancelled()) {
                handle.cancel();
            }
        }
        mainHandles.clear();
        scheduled.clear();
    }

    /**
     * 计算 {@code from} 之后该任务的下一次执行时间，找不到返回 null。
     */
    public LocalDateTime nextExecution(Task task, LocalDateTime from) {
        for (int i = 0; i <= 7; i++) {
            LocalDate date = from.toLocalDate().plusDays(i);
            if (!task.weekdays().contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            LocalDateTime candidate = LocalDateTime.of(date, task.time());
            if (candidate.isAfter(from)) {
                return candidate;
            }
        }
        return null;
    }

    private void schedule(Task task) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = nextExecution(task, now.plusSeconds(1));
        if (next == null) {
            plugin.getLogger().warning("任务 " + task.id() + " 无法计算下次执行时间，已跳过");
            return;
        }
        long waitSeconds = Math.max(0L, Duration.between(now, next).getSeconds());
        long ticks = Math.max(1L, waitSeconds * 20L);
        // 同一任务只保留一个主排程，手动 run 时不会重复排队
        BukkitTask previous = mainHandles.remove(task.id());
        if (previous != null && !previous.isCancelled()) {
            previous.cancel();
        }
        mainHandles.put(task.id(), Bukkit.getScheduler().runTaskLater(plugin, () -> fire(task, false), ticks));
        for (Task.Warning warning : task.warnings()) {
            long warnTicks = (waitSeconds - warning.seconds()) * 20L;
            if (warnTicks > 0L) {
                scheduled.add(Bukkit.getScheduler().runTaskLater(plugin,
                        () -> broadcastWarning(task, warning), warnTicks));
            }
        }
        plugin.getLogger().info("任务 " + task.id() + " 下次执行时间: " + next);
    }

    private void fire(Task task, boolean manual) {
        if (!plugin.isEnabled()) {
            return;
        }
        // 先排下一次，避免本次执行异常导致任务链断掉
        schedule(task);
        if (!manual && !task.enabled()) {
            return;
        }
        World world = Bukkit.getWorld(task.worldName());
        if (world == null) {
            plugin.getLogger().warning("任务 " + task.id() + " 的目标世界 " + task.worldName() + " 不存在，跳过执行");
            return;
        }

        plugin.getLogger().info("开始执行任务 " + task.id() + "（世界 " + world.getName() + "）");

        if (task.teleportEnabled()) {
            int moved = evacuate(task, world);
            plugin.getLogger().info("已把 " + moved + " 名玩家传送出世界 " + world.getName());
        }

        startCommandChain(task);
    }

    /**
     * 手动触发一次任务（/{@code resettools run <id>}）。
     */
    public boolean runNow(Task task) {
        World world = Bukkit.getWorld(task.worldName());
        if (world == null) {
            return false;
        }
        fire(task, true);
        return true;
    }

    private int evacuate(Task task, World world) {
        Location destination = resolveDestination(task, world);
        List<Player> players = new ArrayList<>(world.getPlayers());
        for (Player player : players) {
            try {
                player.eject();
            } catch (RuntimeException ignored) {
                // 玩家不在载具中时部分实现会抛异常，忽略
            }
            player.teleport(destination);
            String template = task.teleportMessage();
            if (template == null) {
                if (!notifyPlayers) {
                    continue;
                }
                template = "&e世界 &f{world} &e即将重置，你已被传送至安全区域";
            }
            player.sendMessage(Text.color(Text.fill(template, world.getName(), task.id(),
                    task.time().toString(), 0, players.size())));
        }
        return players.size();
    }

    private Location resolveDestination(Task task, World source) {
        World destination = task.teleportWorld() == null ? null : Bukkit.getWorld(task.teleportWorld());
        if (destination == null) {
            destination = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (destination == null) {
            throw new IllegalStateException("服务器没有任何已加载的世界，无法传送玩家");
        }
        if (destination.equals(source)) {
            plugin.getLogger().warning("任务的目标世界与传送目标世界相同，玩家不会被传送离开: " + source.getName());
        }
        if (task.explicitLocation()) {
            return new Location(destination, task.x(), task.y(), task.z(), task.yaw(), task.pitch());
        }
        return destination.getSpawnLocation().clone();
    }

    private void broadcastWarning(Task task, Task.Warning warning) {
        World world = Bukkit.getWorld(task.worldName());
        int players = world == null ? 0 : world.getPlayers().size();
        String message = Text.color(Text.fill(warning.message(), task.worldName(), task.id(),
                task.time().toString(), warning.seconds(), players));
        if (message.isBlank()) {
            return;
        }
        if (world != null) {
            for (Player player : world.getPlayers()) {
                player.sendMessage(message);
            }
        } else {
            Bukkit.broadcastMessage(message);
        }
        plugin.getLogger().info("[提醒] " + message);
    }

    /**
     * 命令链路整体在异步线程执行，不在服务器主线程执行。
     */
    private void startCommandChain(Task task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                runSequence(task, task.commands(), 0, 0L,
                        () -> runSequence(task, task.delayCommands(), 0, task.delayTicks(), null)));
    }

    /**
     * 顺序执行一组命令：上一条执行完后再等待 command-interval 秒执行下一条。
     */
    private void runSequence(Task task, List<String> commands, int index, long delayTicks, Runnable onComplete) {
        if (index >= commands.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        String raw = commands.get(index);
        Runnable step = () -> {
            dispatch(task, raw);
            runSequence(task, commands, index + 1, Math.max(1L, (long) task.commandInterval() * 20L), onComplete);
        };
        if (delayTicks <= 0L) {
            step.run();
        } else {
            scheduled.add(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, step, delayTicks));
        }
    }

    private void dispatch(Task task, String raw) {
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        command = Text.fill(command, task.worldName(), task.id(), task.time().toString(), 0, 0);
        plugin.getLogger().info("[任务 " + task.id() + "] 异步执行命令: " + command);
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[任务 " + task.id() + "] 命令执行失败: " + command + " -> " + e.getMessage());
        }
    }
}
