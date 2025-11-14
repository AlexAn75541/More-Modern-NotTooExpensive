package anvil.fix;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public final class Events implements Listener {

/*    private static final Method setMaximumRepairCost;

    static {
        Method method;
        try {
            method = AnvilInventory.class.getMethod("setMaximumRepairCost", int.class);
        } catch (NoSuchMethodException e) {
            method = null;
        }
        setMaximumRepairCost = method;
    }*/

    public static final Map<UUID, Integer> preparing = new ConcurrentHashMap<>();

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player)) return;

        Player player = (Player) event.getView().getPlayer();
        AnvilInventory inv = event.getInventory();

        // Set maximum repair cost to a very high value to prevent "Too Expensive!" message
        inv.setMaximumRepairCost(40_000);

        // If there's no result item, remove player from preparing map
        if (inv.getItem(2) == null) {
            this.onRemove(player);
            return;
        }

        // Get the current repair cost
        int repairCost = inv.getRepairCostAmount();

        ItemStack secondItem = inv.getItem(1);
        if (secondItem == null) {
            this.onRemove(player);
            return;
        }

        ItemStack firstItem = inv.getItem(0);
        if (firstItem == null) {
            this.onRemove(player);
            return;
        }

        // If cost is 0-39, vanilla handles it fine, but we still send creative packet
        // to ensure the level cost is always displayed properly
        if (repairCost >= 0 && repairCost <= 39) {
            preparing.put(player.getUniqueId(), repairCost);
            
            // Send cost messages for all operations
            Bukkit.getLogger().info("[NotTooExpensive] Sending messages for low cost operation to " + player.getName() + " - Cost: " + repairCost);
            AnvilCostCalculator.sendCostChat(player, repairCost);
            AnvilCostCalculator.sendCostActionBar(player, repairCost);
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, PacketListener.create(player, true));
            return;
        }

        // Handle cases where cost is 40 or more (would show "Too Expensive!")

        // Handle enchanted book application
        if (secondItem.getType() == Material.ENCHANTED_BOOK) {
            ItemStack result = firstItem.clone();
            Map<Enchantment, Integer> enchantsOnInput = firstItem.getEnchantments();

            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) secondItem.getItemMeta();
            if (meta == null) return;
            
            Set<Map.Entry<Enchantment, Integer>> enchantsOnBook = meta.getStoredEnchants().entrySet();

            float cost = this.calculateInitialCost(enchantsOnInput);
            int applied = 0;

            for (Map.Entry<Enchantment, Integer> e : enchantsOnBook) {
                Enchantment enchantment = e.getKey();
                if (result.getEnchantmentLevel(enchantment) < e.getValue() && 
                    !this.hasConflicting(enchantsOnInput, enchantment) && 
                    enchantment.canEnchantItem(firstItem)) {
                    result.addUnsafeEnchantment(e.getKey(), e.getValue());
                    applied++;
                    cost += e.getValue() * 1.5f;
                }
            }

            if (applied == 0) return;

            String rename = inv.getRenameText();
            if (rename != null && !rename.isEmpty()) {
                cost++;
                ItemMeta resultMeta = result.getItemMeta();
                if (resultMeta != null) {
                    resultMeta.setDisplayName(rename);
                    result.setItemMeta(resultMeta);
                }
            }

            event.setResult(result);
            inv.setRepairCostAmount((int) cost);
            preparing.put(player.getUniqueId(), (int) cost);
            
            // Send cost messages to player
            Bukkit.getLogger().info("[NotTooExpensive] Sending cost messages to " + player.getName() + " - Cost: " + (int)cost);
            AnvilCostCalculator.sendCostChat(player, (int) cost);
            AnvilCostCalculator.sendCostActionBar(player, (int) cost);
            
            // Send creative mode packet first, then the cost will be displayed properly
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, PacketListener.create(player, true));
        } else {
            // For all other cases (item repair, combining, renaming), just allow it through
            // The setMaximumRepairCost should have already prevented "Too Expensive!"
            // But we still need to track it for packet manipulation
            preparing.put(player.getUniqueId(), repairCost);
            
            // Send cost messages to player
            Bukkit.getLogger().info("[NotTooExpensive] Sending vanilla cost messages to " + player.getName() + " - Cost: " + repairCost);
            AnvilCostCalculator.sendCostChat(player, repairCost);
            AnvilCostCalculator.sendCostActionBar(player, repairCost);
            
            // Send creative mode packet so the cost is shown instead of "Too Expensive!"
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, PacketListener.create(player, true));
        }
    }

    private float calculateInitialCost(Map<Enchantment, Integer> inputEnchants) {
        float cost = 40;
        for (Map.Entry<Enchantment, Integer> e : inputEnchants.entrySet()) {
            cost += e.getValue() / 2.5f;
        }
        //Bukkit.broadcastMessage("INITIAL " + cost);
        return cost;
    }

    private boolean hasConflicting(Map<Enchantment, Integer> enchants, Enchantment toCheckConflict) {
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            if (e.getKey().conflictsWith(toCheckConflict)) return true;
        }
        return false;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getView().getTopInventory().getType() == InventoryType.ANVIL) {
            Player player = (Player) event.getPlayer();
            this.onRemove(player);
            //Bukkit.broadcastMessage("REMOVED");
        }
        //Bukkit.broadcastMessage("CLOSE " + event.getView().getTopInventory().getType());
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        
        // Check if clicking on result slot
        if (event.getRawSlot() != 2) return;
        
        Player player = (Player) event.getWhoClicked();
        Integer cost = preparing.get(player.getUniqueId());
        
        if (cost == null || cost == 0) return;
        
        // Check if player has enough levels
        if (player.getLevel() < cost) {
            event.setCancelled(true);
            AnvilCostCalculator.sendInsufficientLevelsMessage(player, cost, player.getLevel());
            return;
        }
        
        // Deduct the levels
        player.setLevel(player.getLevel() - cost);
        
        // Send success message
        AnvilCostCalculator.sendSuccessMessage(player, cost);
        
        // Clear the preparing map for this player
        preparing.remove(player.getUniqueId());
    }

    private void onRemove(Player player) {
        if (preparing.remove(player.getUniqueId()) != null) PacketEvents.getAPI().getPlayerManager().sendPacket(player, PacketListener.createExact(player));
    }

/*    @EventHandler
    public void onClick(InventoryClickEvent event) {
        //event.getAction()
        Bukkit.broadcastMessage("CLICK " + event.getRawSlot());
    }*/

/*    @EventHandler
    public void onClick(InventoryClickEvent event) {
        //event.getAction()
        
    }

    private void modernHandling(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        try {
            setMaximumRepairCost.invoke(inv, 100_000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void oldHandling(PrepareAnvilEvent event) {
        Player player = (Player) event.getView().getPlayer();
        AnvilInventory inv = event.getInventory();
        ItemStack item = inv.getItem(1);

        if (item == null) return;

        if (inv.getRepairCost() >= 40 && player.getLevel() >= inv.getRepairCost() && item.getType() == Material.ENCHANTED_BOOK) {
            ItemStack res = inv.getItem(0);
            if (res == null) return;

            ItemStack result = res.clone();//already done with "asMirrorCopy", but whatever

            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();

            for (Map.Entry<Enchantment, Integer> e : meta.getStoredEnchants().entrySet()) {
                if (result.getEnchantmentLevel(e.getKey()) < e.getValue())
                    result.addUnsafeEnchantment(e.getKey(), e.getValue());
            }

            event.setResult(result);
            event.getInventory().setRepairCost(0);

        }
    }*/
}