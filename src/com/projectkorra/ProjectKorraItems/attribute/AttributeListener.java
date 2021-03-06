package com.projectkorra.ProjectKorraItems.attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.projectkorra.ProjectKorra.BendingPlayer;
import com.projectkorra.ProjectKorra.Element;
import com.projectkorra.ProjectKorra.Methods;
import com.projectkorra.ProjectKorraItems.Messages;
import com.projectkorra.ProjectKorraItems.ProjectKorraItems;
import com.projectkorra.ProjectKorraItems.items.CustomItem;

public class AttributeListener implements Listener {	
	public static enum ClickType {
		LEFTCLICK, RIGHTCLICK, SHIFT, CONSUME
	}
	public static ConcurrentHashMap<Player, BukkitRunnable> waitingToConfirmClick = new ConcurrentHashMap<Player, BukkitRunnable>();
	public static ConcurrentHashMap<Player, BukkitRunnable> waitingToConfirmShift = new ConcurrentHashMap<Player, BukkitRunnable>();
	// A map of player names that holds their current bending potion effects.
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Attribute>> currentBendingEffects = new ConcurrentHashMap<String, ConcurrentHashMap<String, Attribute>>();
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerSneak(PlayerToggleSneakEvent event) {
		if(event.isCancelled())
			return;
		
		Player player = event.getPlayer();
		startGliding(player);
		
		// Handles the Charges, and ShiftCharges attribute
		if(!player.isSneaking()) {
			tryToConfirmClick(player, waitingToConfirmShift);
			handleEffects(player, ClickType.SHIFT);
			handleItemSource(player, "WaterSource", new ItemStack(Material.POTION));
			//handleItemSource(player, "MetalSource", new ItemStack(Material.IRON_INGOT));
		}
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerSwing(PlayerAnimationEvent event) {
		if(event.isCancelled())
			return;
		Player player = event.getPlayer();
		tryToConfirmClick(player, waitingToConfirmClick);
		handleEffects(player, ClickType.LEFTCLICK);
		handleItemSource(player, "WaterSource", new ItemStack(Material.POTION));
		//handleItemSource(player, "MetalSource", new ItemStack(Material.IRON_INGOT));
	}
		
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerConsume(PlayerItemConsumeEvent event) {
		if(event.isCancelled())
			return;
		handleEffects(event.getPlayer(), ClickType.CONSUME);
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onChangeItem(PlayerItemHeldEvent event) {
		if(event.isCancelled())
			return;
		Player player = event.getPlayer();
		startGliding(player);
	}
	
	public void handleItemSource(Player player, String attrib, ItemStack istack) {
		ConcurrentHashMap<String, Double> attribs = Attribute.getSimplePlayerAttributeMap(player);
		if(attribs.containsKey(attrib) && attribs.get(attrib) == 1) {
			final PlayerInventory inv = player.getInventory();
			int slot = -1;
			for(int i = 9; i < inv.getSize(); i++) {
				if(inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
					slot = i;	
					break;
				}
			}
			if(slot < 0)
				slot = inv.first(Material.AIR);
			if(slot >= 0) {
				inv.setItem(slot, istack);
				player.updateInventory();
			}
			else
				return;
			
			final int fslot = slot;
			new BukkitRunnable() {
				public void run() {
					inv.setItem(fslot, new ItemStack(Material.AIR));
				}
			}.runTaskLater(ProjectKorraItems.plugin, 10);
		}
	}
	
	
	public void startGliding(Player p) {
		final Player player = p;
		BendingPlayer bplayer = Methods.getBendingPlayer(player.getName());
		if(player == null || bplayer == null)
			return;
		
		if(!player.isSneaking() && player.getLocation().getBlock().getType() == Material.AIR
				&& bplayer.hasElement(Element.Air)) {
			new BukkitRunnable() {
				int counter = 0;
				public void run() {
					Block block = player.getLocation().add(0, -0.5, 0).getBlock();
					if(block.getType() != Material.AIR) {
						this.cancel();
						return;
					}
					ConcurrentHashMap<String, Double> attribs = Attribute.getSimplePlayerAttributeMap(player);
					boolean auto = Attribute.getBooleanValue("AirGlideAutomatic", attribs);					
					if(!player.isSneaking() && !auto) {
						this.cancel();
						return;
					}
					
					if(attribs.containsKey("AirGlide")) {
						double speed = AttributeList.AIR_GLIDE_SPEED;
						double fallSpeed = AttributeList.AIR_GLIDE_FALL;
						if(attribs.containsKey("AirGlideSpeed")) 
							speed = speed + speed * attribs.get("AirGlideSpeed") / 100.0;
						if(attribs.containsKey("AirGlideFallSpeed")) 
							fallSpeed = fallSpeed + fallSpeed * attribs.get("AirGlideFallSpeed") / 100.0;
						
						Location loc = player.getEyeLocation();
						loc.setPitch(0);
						Vector vel = loc.getDirection();
						vel.normalize();						
						vel.multiply(speed);
						vel.setY(fallSpeed);
						player.setFallDistance(0);
						player.setVelocity(vel);
						if(counter == 0)
							updateCharges(player, ClickType.SHIFT);
						counter++;
					}
				}
			}.runTaskTimer(ProjectKorraItems.plugin, 1, 1);
		}
	}
	
	/*
	 * When a player left clicks we can't be completely sure that
	 * the ability actually executed. If it didn't execute we can't take away
	 * a charge or click charge.
	 * 
	 * To determine if the click executed we will store the players name temporarily
	 * and then wait for the AbilityUpdater to let us know if it went through.
	 */
	public static void tryToConfirmClick(Player player, ConcurrentHashMap<Player, BukkitRunnable> map) {
		if(map.containsKey(player)) {
			map.get(player).cancel();
			waitingToConfirmClick.remove(player);
		}
		
		final Player fplayer = player;
		final ConcurrentHashMap<Player, BukkitRunnable> fmap = map;
		BukkitRunnable br = new BukkitRunnable() {
			public void run() {
				fmap.remove(fplayer);
			}
		};
		br.runTaskLater(ProjectKorraItems.plugin, 3);
		map.put(player, br);
	}
	
	public static void confirmClick(Player player, ConcurrentHashMap<Player, BukkitRunnable> map, ClickType type) {
		if(map.containsKey(player)) {
			map.get(player).cancel();
			map.remove(player);
			updateCharges(player, type);
		}
	}	
	
	public static ArrayList<ItemStack> getPlayerItems(Player player) {
		ArrayList<ItemStack> istacks = new ArrayList<ItemStack>();
		for(ItemStack istack : player.getInventory().getArmorContents())
			istacks.add(istack);
		istacks.add(player.getItemInHand());
		return istacks;
	}
	
	public static void handleEffects(Player player, ClickType type) {
		if(player == null)
			return;
		
		ArrayList<ItemStack> istacks = getPlayerItems(player);
		String[] validAttribs = null;
		if(type == ClickType.LEFTCLICK) validAttribs = new String[]{"Effects", "ClickEffects"};
		else if(type == ClickType.SHIFT) validAttribs = new String[]{"Effects", "SneakEffects"};
		else if(type == ClickType.CONSUME) validAttribs = new String[]{"Effects", "ConsumeEffects"};
		else
			validAttribs = new String[]{"Effects"};
		
		boolean effectAdded = false;
		for(ItemStack istack : istacks) {
			CustomItem citem = CustomItem.getCustomItem(istack);
			if(citem == null)
				continue;
			
			for(Attribute att : citem.getAttributes())
				for(String allowedEff : validAttribs) 
					if(att.getName().equalsIgnoreCase(allowedEff)) {
						ArrayList<PotionEffect> potEffects = Attribute.parsePotionEffects(att);
						ArrayList<Attribute> bendEffects = Attribute.parseBendingEffects(att);
						
						for(PotionEffect pot : potEffects)
							player.addPotionEffect(pot, true);
						effectAdded = true;
						
						for(Attribute effect : bendEffects) {
							if(!currentBendingEffects.containsKey(player.getName()))
								currentBendingEffects.put(player.getName(), new ConcurrentHashMap<String, Attribute>());
							effect.setTime(System.currentTimeMillis());
							ConcurrentHashMap<String, Attribute> playerEffList = currentBendingEffects.get(player.getName());
							playerEffList.put(effect.getName(), effect);
						}
					}
		}
		if(effectAdded) 
			updateCharges(player, type);
	}
		
	public static void updateCharges(Player player, ClickType type) {
		if(player == null)
			return;
		
		ArrayList<ItemStack> istacks = getPlayerItems(player);
		for(ItemStack istack : istacks) {
			CustomItem citem = CustomItem.getCustomItem(istack);
			if(citem == null)
				continue;
			ItemMeta meta = istack.getItemMeta();
			List<String> lore = meta.getLore();
			if(lore == null)
				continue;
			
			boolean displayDestroyMsg = false;
			List<String> newLore = new ArrayList<String>();
			for(String line : lore) {
				String newLine = line;
				try {
					if(line.startsWith(AttributeList.CHARGES_STR) 
							|| (line.startsWith(AttributeList.CLICK_CHARGES_STR) && type == ClickType.LEFTCLICK)
							|| (line.startsWith(AttributeList.SNEAK_CHARGES_STR) && type == ClickType.SHIFT)) {
						String start = line.substring(0, line.indexOf(": "));
						String end = line.substring(line.indexOf(": ") + 1, line.length());
						end = end.trim();
						int val = Integer.parseInt(end) - 1;
						if(val == 0)
							displayDestroyMsg = true;
						if(val >= 0)
							newLine = start + ": " + val;
					}
				}
				catch (Exception e) {}
				newLore.add(newLine);
			}
			meta.setLore(newLore);
			istack.setItemMeta(meta);
			
			//Check if we need to destroy the item
			boolean hasDestroyAttr = false;
			boolean hasIgnoreDestroyMsg = false;
			for(Attribute attr : citem.getAttributes()) {
				if(attr.getBooleanValue("DestroyAfterCharges") == true)
					hasDestroyAttr = true;
				else if(attr.getBooleanValue("IgnoreDestroyMessage") == true)
					hasIgnoreDestroyMsg = true;
			}
			
			boolean hasChargesLeft = true;
			for(String line : newLore) {
				try {
					if(line.startsWith(AttributeList.CHARGES_STR) || line.startsWith(AttributeList.CLICK_CHARGES_STR)
							|| line.startsWith(AttributeList.SNEAK_CHARGES_STR)) {
						String tmpStr = line.substring(line.indexOf(": ") + 1, line.length()).trim();
						int value = Integer.parseInt(tmpStr);
						if(value <= 0)
							hasChargesLeft = false;
						else {
							hasChargesLeft = true;
							break;
						}
					}
				}
				catch(Exception e) {}
			}	
			
			/*
			 * When we go to destroy an item we need to check that there
			 * were not multiple items in that stack. If there were
			 * multiple items then we need to just remove 1 and change the lore
			 * back to the start.
			 */
			if(!hasChargesLeft && !hasIgnoreDestroyMsg && displayDestroyMsg)
				player.sendMessage(citem.getDisplayName() + " " + Messages.ITEM_DESTROYED);
			if(hasDestroyAttr && !hasChargesLeft) {
				if(player.getInventory().contains(istack)) {
					if(istack.getAmount() > 1) {
						istack.setAmount(istack.getAmount() - 1);
						ItemStack newStack = citem.generateItem();
						setLore(istack, newStack.getItemMeta().getLore());
					}
					else
						player.getInventory().remove(istack);
				}
				else {
					ItemStack[] armor = player.getInventory().getArmorContents();
					for(int i = 0; i < armor.length; i++) {
						if(armor[i].equals(istack)) {
							if(istack.getAmount() > 1) {
								armor[i].setAmount(armor[i].getAmount() - 1);
								ItemStack newStack = citem.generateItem();
								setLore(armor[i], newStack.getItemMeta().getLore());
							}
							else
								armor[i] = new ItemStack(Material.AIR);
							break;
						}
					}
					player.getInventory().setArmorContents(armor);
				}
			}
			
		}
	}
	
	public static boolean setLore(ItemStack istack, List<String> lore) {
		if(istack == null || lore == null)
			return false;
		ItemMeta meta = istack.getItemMeta();
		if(meta == null)
			return false;
		meta.setLore(lore);
		istack.setItemMeta(meta);
		return true;
	}
}
