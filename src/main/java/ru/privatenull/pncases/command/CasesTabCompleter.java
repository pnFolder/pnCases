package ru.privatenull.pncases.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.privatenull.pncases.cases.CaseManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Lightweight completion over data already loaded in memory. */
public final class CasesTabCompleter implements TabCompleter {

    private final CaseManager caseManager;

    public CasesTabCompleter(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean canGift = sender instanceof Player && sender.hasPermission(caseManager.getPlugin().giftPermission());
        if (args.length == 1 && !sender.hasPermission("pncases.admin")) {
            return canGift ? filter(List.of("gift"), args[0]) : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gift") && canGift) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getName().equalsIgnoreCase(sender.getName())) players.add(player.getName());
            }
            return filter(players, args[1]);
        }
        if (!sender.hasPermission("pncases.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(List.of("createcase", "copycase", "setcase", "delcase", "machine",
                    "givekey", "takekey", "giftlimit", "workbench", "reload"));
            if (canGift) commands.add("gift");
            return filter(commands, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (subcommand.equals("setcase") || subcommand.equals("copycase")
                || subcommand.equals("delcase") || subcommand.equals("machine") || subcommand.equals("workbench"))) {
            return filter(caseManager.getConfiguredCaseNames(), args[1]);
        }
        if (subcommand.equals("giftlimit")) {
            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
                return filter(players, args[1]);
            }
            return args.length == 3 ? filter(List.of("reset"), args[2]) : List.of();
        }
        if (!subcommand.equals("givekey") && !subcommand.equals("takekey")) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return filter(players, args[1]);
        }
        if (args.length == 3) {
            return filter(caseManager.getKeyNames(), args[2]);
        }
        if (args.length == 4) {
            return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[3]);
        }
        if (args.length == 5 && subcommand.equals("givekey")) {
            return filter(List.of("-s"), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> values, String prefix) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, values, matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }
}
