package me.badbones69.epicsellchest.controlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.badbones69.epicsellchest.Main;
import me.badbones69.epicsellchest.Methods;
import me.badbones69.epicsellchest.api.EpicSellChest;
import me.badbones69.epicsellchest.api.Messages;
import me.badbones69.epicsellchest.api.SellItem;
import me.badbones69.epicsellchest.api.SellType;
import me.badbones69.epicsellchest.api.currency.Currency;
import me.badbones69.epicsellchest.api.currency.CustomCurrency;
import me.badbones69.epicsellchest.api.event.SellChestEvent;

public class SellChestGUI implements Listener {
	
	private static EpicSellChest sc = EpicSellChest.getInstance();
	private HashMap<UUID, ArrayList<SellItem>> sellables = new HashMap<UUID, ArrayList<SellItem>>();
	private HashMap<UUID, ArrayList<ItemStack>> nonsellables = new HashMap<UUID, ArrayList<ItemStack>>();
	
	public static void openSellChestGUI(Player player) {
		sc.openSellChestGUI(player);
	}
	
	@EventHandler
	public void onSellChest(InventoryCloseEvent e) {
		Inventory inv = e.getInventory();
		Player player = (Player) e.getPlayer();
		UUID uuid = player.getUniqueId();
		FileConfiguration config = Main.settings.getConfig();
		if(inv != null) {
			if(inv.getName().equalsIgnoreCase(Methods.color(config.getString("Settings.Sign-Options.Inventory-Name")))) {
				if(!Methods.isInvEmpty(inv)) {
					if(!sc.needsTwoFactorAuth(uuid)) {
						ArrayList<SellItem> items = sc.getSellableItems(inv);
						if(items.size() > 0) {
							SellChestEvent event = new SellChestEvent(player, items, SellType.GUI);
							Bukkit.getPluginManager().callEvent(event);
							if(!event.isCancelled()) {
								HashMap<String, Integer> placeholders = new HashMap<>();
								for(Currency currency : Currency.values()) {
									placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
									placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
								}
								for(CustomCurrency currency : sc.getCustomCurrencies()) {
									placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
									placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
								}
								sc.sellSellableItems(player, items);
								for(SellItem item : items) {
									inv.remove(item.getItem());
								}
								sc.removeTwoFactorAuth(uuid);
								player.sendMessage(Messages.SOLD_CHEST.getMessageInt(placeholders));
							}
						}else {
							player.sendMessage(Messages.NO_SELLABLE_ITEMS.getMessage());
						}
						for(ItemStack item : inv.getContents()) {
							if(item != null) {
								if(Methods.isInvFull(player)) {
									player.getWorld().dropItemNaturally(player.getLocation(), item);
								}else {
									player.getInventory().addItem(item);
								}
							}
						}
						inv.clear();
					}else {
						ArrayList<SellItem> items = sc.getSellableItems(inv);
						sellables.put(uuid, items);
						for(SellItem item : items) {
							inv.remove(item.getItem());
						}
						ArrayList<ItemStack> others = new ArrayList<ItemStack>();
						for(ItemStack item : inv.getContents()) {
							if(item != null) {
								others.add(item);
							}
						}
						nonsellables.put(uuid, others);
						inv.clear();
						new BukkitRunnable() {
							@Override
							public void run() {
								openTwoFactorAuth(player);
							}
						}.runTaskLater(sc.getPlugin(), 0);
					}
				}
			}else if(inv.getName().equalsIgnoreCase(Methods.color(Main.settings.getConfig().getString("Settings.Sign-Options.Two-Factor-Auth-Options.Inventory-Name")))) {
				if(sellables.containsKey(uuid) && nonsellables.containsKey(uuid)) {
					ArrayList<SellItem> items = sellables.get(uuid);
					if(items.size() > 0) {
						SellChestEvent event = new SellChestEvent(player, items, SellType.GUI);
						Bukkit.getPluginManager().callEvent(event);
						if(!event.isCancelled()) {
							HashMap<String, Integer> placeholders = new HashMap<>();
							for(Currency currency : Currency.values()) {
								placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
								placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
							}
							for(CustomCurrency currency : sc.getCustomCurrencies()) {
								placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
								placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
							}
							sc.sellSellableItems(player, items);
							player.sendMessage(Messages.SOLD_CHEST.getMessageInt(placeholders));
						}
					}else {
						player.sendMessage(Messages.NO_SELLABLE_ITEMS.getMessage());
					}
					for(ItemStack item : nonsellables.get(uuid)) {
						if(item != null) {
							if(Methods.isInvFull(player)) {
								player.getWorld().dropItemNaturally(player.getLocation(), item);
							}else {
								player.getInventory().addItem(item);
							}
						}
					}
					inv.clear();
					sellables.remove(uuid);
					nonsellables.remove(uuid);
				}
			}
		}
	}
	
	@EventHandler
	public void onTwoFactorAuth(InventoryClickEvent e) {
		Player player = (Player) e.getWhoClicked();
		UUID uuid = player.getUniqueId();
		Inventory inv = e.getInventory();
		if(inv != null) {
			if(inv.getName().equalsIgnoreCase(Methods.color(Main.settings.getConfig().getString("Settings.Sign-Options.Two-Factor-Auth-Options.Inventory-Name")))) {
				e.setCancelled(true);
				if(sellables.containsKey(uuid) && nonsellables.containsKey(uuid)) {
					ItemStack check = e.getCurrentItem();
					if(check != null) {
						if(check.isSimilar(getAcceptItem())) {
							ArrayList<SellItem> items = sellables.get(uuid);
							if(items.size() > 0) {
								SellChestEvent event = new SellChestEvent(player, items, SellType.GUI);
								Bukkit.getPluginManager().callEvent(event);
								if(!event.isCancelled()) {
									HashMap<String, Integer> placeholders = new HashMap<>();
									for(Currency currency : Currency.values()) {
										placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
										placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
									}
									for(CustomCurrency currency : sc.getCustomCurrencies()) {
										placeholders.put("%" + currency.getName().toLowerCase() + "%", sc.getFullCost(player, items, currency));
										placeholders.put("%" + currency.getName() + "%", sc.getFullCost(player, items, currency));
									}
									sc.sellSellableItems(player, items);
									player.sendMessage(Messages.SOLD_CHEST.getMessageInt(placeholders));
								}
							}else {
								player.sendMessage(Messages.NO_SELLABLE_ITEMS.getMessage());
							}
							for(ItemStack item : nonsellables.get(uuid)) {
								if(item != null) {
									if(Methods.isInvFull(player)) {
										player.getWorld().dropItemNaturally(player.getLocation(), item);
									}else {
										player.getInventory().addItem(item);
									}
								}
							}
							inv.clear();
							sellables.remove(uuid);
							nonsellables.remove(uuid);
							player.closeInventory();
						}else if(check.isSimilar(getDenyItem())) {
							for(SellItem item : sellables.get(uuid)) {
								if(item != null) {
									if(Methods.isInvFull(player)) {
										player.getWorld().dropItemNaturally(player.getLocation(), item.getItem());
									}else {
										player.getInventory().addItem(item.getItem());
									}
								}
							}
							for(ItemStack item : nonsellables.get(uuid)) {
								if(item != null) {
									if(Methods.isInvFull(player)) {
										player.getWorld().dropItemNaturally(player.getLocation(), item);
									}else {
										player.getInventory().addItem(item);
									}
								}
							}
							inv.clear();
							sellables.remove(uuid);
							nonsellables.remove(uuid);
							player.closeInventory();
						}
					}
				}
			}
		}
	}
	
	private void openTwoFactorAuth(Player player) {
		Inventory inv = Bukkit.createInventory(null, 9, Methods.color(Main.settings.getConfig().getString("Settings.Sign-Options.Two-Factor-Auth-Options.Inventory-Name")));
		ItemStack accept = getAcceptItem();
		ItemStack deny = getDenyItem();
		inv.setItem(0, accept.clone());
		inv.setItem(1, accept.clone());
		inv.setItem(2, accept.clone());
		inv.setItem(3, accept.clone());
		inv.setItem(4, getInfoItem());
		inv.setItem(5, deny.clone());
		inv.setItem(6, deny.clone());
		inv.setItem(7, deny.clone());
		inv.setItem(8, deny.clone());
		player.openInventory(inv);
	}
	
	private ItemStack getAcceptItem() {
		String path = "Settings.Sign-Options.Two-Factor-Auth-Options";
		return Methods.makeItem(Main.settings.getConfig().getString(path + ".Accept.Item"), 1, Main.settings.getConfig().getString(path + ".Accept.Name"), Main.settings.getConfig().getStringList(path + ".Accept.Lore"));
	}
	
	private ItemStack getInfoItem() {
		String path = "Settings.Sign-Options.Two-Factor-Auth-Options";
		return Methods.makeItem(Main.settings.getConfig().getString(path + ".Info.Item"), 1, Main.settings.getConfig().getString(path + ".Info.Name"), Main.settings.getConfig().getStringList(path + ".Info.Lore"));
	}
	
	private ItemStack getDenyItem() {
		String path = "Settings.Sign-Options.Two-Factor-Auth-Options";
		return Methods.makeItem(Main.settings.getConfig().getString(path + ".Deny.Item"), 1, Main.settings.getConfig().getString(path + ".Deny.Name"), Main.settings.getConfig().getStringList(path + ".Deny.Lore"));
	}
	
}