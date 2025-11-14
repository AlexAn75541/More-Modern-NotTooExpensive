package anvil.fix;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Set;

public class AnvilCostCalculator {

    /**
     * Calculate the cost for applying an enchanted book to an item
     */
    public static int calculateEnchantedBookCost(ItemStack item, ItemStack book, String rename) {
        if (book.getType() != Material.ENCHANTED_BOOK) {
            return 0;
        }

        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
        if (bookMeta == null) {
            return 0;
        }

        Map<Enchantment, Integer> itemEnchants = item.getEnchantments();
        Set<Map.Entry<Enchantment, Integer>> bookEnchants = bookMeta.getStoredEnchants().entrySet();

        // Start with base cost based on existing enchantments
        float cost = calculateBaseCost(itemEnchants);
        int applied = 0;

        // Calculate cost for each enchantment from the book
        for (Map.Entry<Enchantment, Integer> entry : bookEnchants) {
            Enchantment enchantment = entry.getKey();
            int bookLevel = entry.getValue();
            int itemLevel = item.getEnchantmentLevel(enchantment);

            // Check if enchantment can be applied
            if (itemLevel < bookLevel && 
                !hasConflictingEnchantment(itemEnchants, enchantment) && 
                enchantment.canEnchantItem(item)) {
                
                // Cost increases based on enchantment level
                cost += bookLevel * 1.5f;
                applied++;
            }
        }

        // If no enchantments were applied, return 0
        if (applied == 0) {
            return 0;
        }

        // Add rename cost
        if (rename != null && !rename.isEmpty()) {
            cost += 1;
        }

        return (int) cost;
    }

    /**
     * Calculate the cost for vanilla anvil operations (repair, combine, rename)
     */
    public static int calculateVanillaCost(ItemStack first, ItemStack second, String rename) {
        int cost = 0;

        // Calculate repair cost if same item type
        if (first.getType() == second.getType() && first.getType().getMaxDurability() > 0) {
            ItemMeta firstMeta = first.getItemMeta();
            ItemMeta secondMeta = second.getItemMeta();
            
            if (firstMeta instanceof Damageable && secondMeta instanceof Damageable) {
                int firstDamage = ((Damageable) firstMeta).getDamage();
                int secondDamage = ((Damageable) secondMeta).getDamage();
                
                // Calculate repair percentage
                if (firstDamage > 0 || secondDamage > 0) {
                    int maxDurability = first.getType().getMaxDurability();
                    int repairAmount = maxDurability - secondDamage;
                    
                    // Cost based on how much is being repaired
                    cost += Math.max(1, (repairAmount * 2) / maxDurability);
                }
            }
        }

        // Add enchantment combination cost
        Map<Enchantment, Integer> firstEnchants = first.getEnchantments();
        Map<Enchantment, Integer> secondEnchants = second.getEnchantments();
        
        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();
            int firstLevel = firstEnchants.getOrDefault(ench, 0);
            
            // Cost varies based on whether we're upgrading or combining same level
            if (secondLevel > firstLevel) {
                // Upgrading enchantment
                cost += (secondLevel - firstLevel) * ench.getAnvilCost();
            } else if (secondLevel == firstLevel && secondLevel < ench.getMaxLevel()) {
                // Combining same level to get higher level
                cost += (secondLevel + 1) * ench.getAnvilCost();
            }
        }

        // Add rename cost
        if (rename != null && !rename.isEmpty()) {
            ItemMeta firstMeta = first.getItemMeta();
            String currentName = firstMeta != null && firstMeta.hasDisplayName() ? 
                                 firstMeta.getDisplayName() : "";
            
            // Only charge if name is actually changing
            if (!rename.equals(currentName)) {
                cost += 1;
            }
        }

        // Minimum cost for any operation
        if (cost == 0 && (second.getType() != Material.AIR || 
                         (rename != null && !rename.isEmpty()))) {
            cost = 1;
        }

        return Math.max(cost, 0);
    }

    /**
     * Calculate base cost based on existing enchantments on an item
     */
    private static float calculateBaseCost(Map<Enchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) {
            return 1.0f;
        }

        float cost = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            // Higher level enchantments increase base cost
            cost += entry.getValue() / 2.5f;
        }
        
        // Minimum base cost
        return Math.max(cost, 1.0f);
    }

    /**
     * Check if an enchantment conflicts with existing enchantments
     */
    private static boolean hasConflictingEnchantment(Map<Enchantment, Integer> enchantments, 
                                                     Enchantment toCheck) {
        for (Enchantment existing : enchantments.keySet()) {
            if (existing.conflictsWith(toCheck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send cost information to player via action bar
     */
    public static void sendCostActionBar(Player player, int cost) {
        if (cost <= 0) {
            return;
        }

        Bukkit.getLogger().info("[NotTooExpensive] Sending action bar to " + player.getName() + " with cost: " + cost);
        player.sendActionBar(Component.text()
                .append(Component.text("Experience Required: ").color(NamedTextColor.YELLOW))
                .append(Component.text(cost + " levels").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .build());
    }

    /**
     * Send cost information to player via chat
     */
    public static void sendCostChat(Player player, int cost) {
        if (cost <= 0) {
            return;
        }

        Bukkit.getLogger().info("[NotTooExpensive] Sending chat message to " + player.getName() + " with cost: " + cost);
        player.sendMessage(Component.text()
                .append(Component.text("╔══════════════════════╗").color(NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("  Anvil Cost: ").color(NamedTextColor.GRAY))
                .append(Component.text(cost + " levels").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("╚══════════════════════╝").color(NamedTextColor.DARK_GRAY))
                .build());
    }

    /**
     * Send error message when player doesn't have enough experience
     */
    public static void sendInsufficientLevelsMessage(Player player, int required, int current) {
        player.sendMessage(Component.text()
                .append(Component.text("✘ ").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("Not enough experience!").color(NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("  Required: ").color(NamedTextColor.GRAY))
                .append(Component.text(required + " levels").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("  Current: ").color(NamedTextColor.GRAY))
                .append(Component.text(current + " levels").color(NamedTextColor.WHITE))
                .build());
        
        player.sendActionBar(Component.text()
                .append(Component.text("Not enough experience! Required: ").color(NamedTextColor.RED))
                .append(Component.text(required + " levels").color(NamedTextColor.YELLOW))
                .build());
    }

    /**
     * Send success message after anvil operation
     */
    public static void sendSuccessMessage(Player player, int cost) {
        player.sendActionBar(Component.text()
                .append(Component.text("✔ ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text("Item crafted! ").color(NamedTextColor.GREEN))
                .append(Component.text("(-" + cost + " levels)").color(NamedTextColor.GRAY))
                .build());
    }
}
