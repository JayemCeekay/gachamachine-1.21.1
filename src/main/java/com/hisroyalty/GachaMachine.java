package com.hisroyalty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hisroyalty.block.DummyGachaMachineBlock;
import com.hisroyalty.block.GachaMachineBlock;
import com.hisroyalty.item.GachaItemRegistry;
import com.hisroyalty.mixin.LootContextTypesAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

public class GachaMachine implements ModInitializer {
	public static final String MOD_ID = "gachamachine";



	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	public static final Block GACHA_MACHINE = Registry.register(Registries.BLOCK, id("gacha_machine"),
			new GachaMachineBlock(AbstractBlock.Settings.create().sounds(BlockSoundGroup.METAL).nonOpaque()));

	public static final Block GACHA_MACHINE_DUMMY = Registry.register(Registries.BLOCK, id("dummy_gacha_machine"),
			new DummyGachaMachineBlock(AbstractBlock.Settings.create().sounds(BlockSoundGroup.METAL).nonOpaque()));
	public static final RegistryKey<ItemGroup> GACHA_ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), id("item_group"));
	public static final ItemGroup GACHA_ITEM_GROUP = Registry.register(Registries.ITEM_GROUP, GACHA_ITEM_GROUP_KEY,
			FabricItemGroup.builder().icon(() -> new ItemStack(GachaItemRegistry.GACHA_MACHINE)).displayName(Text.translatable("itemGroup.gacha_machines")).entries((c, e) -> {
				e.add(GachaItemRegistry.GACHA_MACHINE);
				//e.add(GachaItemRegistry.RED_CAPSULE);
				//e.add(GachaItemRegistry.GREEN_CAPSULE);
				//e.add(GachaItemRegistry.YELLOW_CAPSULE);
				e.add(GachaItemRegistry.BASIC_CAPSULE);
				e.add(GachaItemRegistry.COPPER_CAPSULE);
				e.add(GachaItemRegistry.IRON_CAPSULE);
				e.add(GachaItemRegistry.DIAMOND_CAPSULE);
				e.add(GachaItemRegistry.NETHERITE_CAPSULE);
			}).build());



	public static final LootContextType GACHA_MACHINE_LOOT_CONTEXT = registerLootContext(id("gacha_machine"), b -> b.require(LootContextParameters.ORIGIN).require(LootContextParameters.THIS_ENTITY));
	public static final LootContextType CAPSULE_LOOT_CONTEXT = registerLootContext(id("capsule"), b -> b.require(LootContextParameters.ORIGIN).allow(LootContextParameters.THIS_ENTITY));


	@Override
	public void onInitialize() {
		GachaItemRegistry.init();
		initConfig();
	}

	public void initConfig() {
		File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "gachamachine");

		if (!configDir.exists() && !configDir.mkdirs()) {
			throw new RuntimeException("Failed to create config directory: " + configDir.getAbsolutePath());
		}

		File configFile = new File(configDir, "config.json");

		if (!configFile.exists()) {
			JsonObject defaultConfig = new JsonObject();
			defaultConfig.addProperty("useImpactorEconomy", false); // New toggle for Impactor Economy
			defaultConfig.addProperty("impactorAmount", 10);       // Default Impactor Currency amount

			try {
				Files.writeString(configFile.toPath(), new Gson().toJson(defaultConfig), StandardCharsets.UTF_8);
				System.out.println("Default config file created: " + configFile.getAbsolutePath());
			} catch (IOException e) {
				throw new RuntimeException("Failed to create config file: " + configFile.getAbsolutePath(), e);
			}
		}
	}

	public static boolean useImpactorEconomy() {
		return getConfigProperty("useImpactorEconomy", false);
	}

	public static int getImpactorAmount() {
		return getConfigProperty("impactorAmount", 100);
	}

	private static <T> T getConfigProperty(String key, T defaultValue) {
		File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "gachamachine/config.json");
		if (configFile.exists()) {
			try (FileReader reader = new FileReader(configFile)) {
				JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
				if (config.has(key)) {
					return (T) new Gson().fromJson(config.get(key), defaultValue.getClass());
				}
			} catch (IOException e) {
				System.err.println("Failed to read config: " + e.getMessage());
			}
		}
		return defaultValue;
	}


	protected static LootContextType registerLootContext(Identifier id, Consumer<LootContextType.Builder> type) {
		LootContextType.Builder builder = new LootContextType.Builder();
		type.accept(builder);
		LootContextType lootContextType = builder.build();
		LootContextType check = LootContextTypesAccessor.getMAP().put(id, lootContextType);
		if (check != null) throw new IllegalStateException("Loot table parameter set " + id + " is already registered");
		return lootContextType;
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}


}








