package com.hisroyalty.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hisroyalty.GachaMachine;
import com.hisroyalty.ModTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.hisroyalty.GachaMachine.GACHA_MACHINE_LOOT_CONTEXT;
import static com.hisroyalty.GachaMachine.MOD_ID;

public class GachaMachineBlock extends Block {
    public static final IntProperty LEVEL_X = IntProperty.of("level", 0, deserializeLevel());
    public static final IntProperty LEVEL = LEVEL_X;
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;


    public GachaMachineBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(LEVEL, 0).with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
        builder.add(FACING);
        builder.add(HALF);

    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getOrEmpty(HALF).orElse(null) == DoubleBlockHalf.UPPER) return;
        super.onStateReplaced(state, world, pos, newState, moved);
    }


    private static int deserializeLevel() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        File configFile = new File(configDir, "gachamachine/config.json");
        if (!configFile.exists()) {
            System.err.println("Config file not found: " + configFile.getAbsolutePath());
        }
        try (FileReader reader = new FileReader(configFile)) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            DataResult<Integer> result = Codec.INT.parse(JsonOps.INSTANCE, jsonElement);

            Integer value = result.resultOrPartial(System.err::println).orElse(3);
            return value;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 3;

    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.up()).isReplaceable();
    }


    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        if (state.get(HALF) == DoubleBlockHalf.UPPER) {
            return Collections.emptyList();
        }
        return super.getDroppedStacks(state, builder);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient())
            world.setBlockState(pos.up(), this.getDefaultState().with(HALF, DoubleBlockHalf.UPPER).with(FACING, state.get(FACING)));
        super.onPlaced(world, pos, state, placer, itemStack);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis() == Direction.Axis.Y) {
            boolean valid = true;
            DoubleBlockHalf half = state.get(HALF);

            if (half == DoubleBlockHalf.LOWER && direction == Direction.UP)
                if (!neighborState.isOf(this) || neighborState.get(HALF) != DoubleBlockHalf.UPPER)
                    valid = false;
            if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN)
                if (!neighborState.isOf(this) || neighborState.get(HALF) != DoubleBlockHalf.LOWER)
                    valid = false;

            if (!valid) return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    private boolean isValidItem(ItemStack stack) {
        return stack.isIn(ModTags.Items.CURRENCY_ITEMS);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if(world.isClient) {
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {

        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }

        if (GachaMachine.useImpactorEconomy()) {
            // Check Impactor Economy balance
            int requiredAmount = GachaMachine.getImpactorAmount();

            try {
                if (EconomyService.instance().account(player.getUuid()).get().balance().intValue() >= requiredAmount) {
                    // Deduct the required amount
                    EconomyService.instance().account(player.getUuid()).get().withdraw(BigDecimal.valueOf(requiredAmount));

                    // Handle successful Impactor economy transaction
                    ItemStack capsule = getOutput(player);
                    Direction direction = state.get(FACING);

                    BlockPos spawnPos = pos;
                    if (state.get(HALF) == DoubleBlockHalf.UPPER) {
                        spawnPos = pos.down();
                    }

                    double offsetX = direction.getOffsetX() * 0.5;
                    double offsetZ = direction.getOffsetZ() * 0.5;

                    ItemEntity capsuleEntity = new ItemEntity(
                            world,
                            spawnPos.getX() + 0.5 + offsetX,
                            spawnPos.getY() + 0.5,
                            spawnPos.getZ() + 0.5 + offsetZ,
                            capsule
                    );

                    capsuleEntity.setVelocity(
                            direction.getOffsetX() * 0.2,
                            0.0,
                            direction.getOffsetZ() * 0.2
                    );

                    world.spawnEntity(capsuleEntity);
                    updateLevel(world, pos, state, 0);

                    world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    return ItemActionResult.SUCCESS;
                } else {
                    // Insufficient funds
                    player.sendMessage(Text.translatable("message.gacha_machine.insufficient_funds"), true);
                    return ItemActionResult.FAIL;
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        // Fallback to original item-based economy
        return handleItemBasedEconomy(stack, state, world, pos, player, hand, hit);
    }

    private ItemActionResult handleItemBasedEconomy(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!isValidItem(stack)) {
            // Handle invalid item logic
            TagKey<Item> currencyTag = ModTags.Items.CURRENCY_ITEMS;
            StringBuilder itemNames = new StringBuilder();

            for (RegistryEntry<Item> itemRegistryEntry : Registries.ITEM.getOrCreateEntryList(currencyTag)) {
                String itemName = itemRegistryEntry.getKey().get().getValue().getPath();
                itemNames.append(itemName).append(", ");
            }

            if (itemNames.length() > 0) {
                itemNames.setLength(itemNames.length() - 2);
            }

            Text message = Text.translatable("message.gacha_machine.invalid_currency",
                    Text.literal(itemNames.toString()).styled(style -> style.withColor(Formatting.RED))
            ).styled(style -> style.withColor(Formatting.RED));

            if (message.getString().length() <= 128) {
                player.sendMessage(message, true);
            } else {
                player.sendMessage(message, false);
            }

            return ItemActionResult.FAIL;
        }

        if (isValidItem(stack)) {
            int currentLevel = state.get(LEVEL);

            if (currentLevel < (deserializeLevel() - 1)) {
                updateLevel(world, pos, state, currentLevel + 1);

                Text text = Text.literal("[").append(Text.of(String.valueOf(currentLevel + 1)))
                        .append(Text.of("/"))
                        .append(Text.of(String.valueOf(deserializeLevel()))).append(Text.of("]"));
                player.sendMessage(text, true);

                if (!player.isCreative()) {
                    stack.decrement(1);
                }

                world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.0F);

                return ItemActionResult.SUCCESS;
            } else {
                if (!world.isClient) {
                    // Generate and spawn capsule
                    ItemStack capsule = getOutput(player);
                    Direction direction = state.get(FACING);

                    BlockPos spawnPos = pos;
                    if (state.get(HALF) == DoubleBlockHalf.UPPER) {
                        spawnPos = pos.down();
                    }

                    double offsetX = direction.getOffsetX() * 0.5;
                    double offsetZ = direction.getOffsetZ() * 0.5;

                    ItemEntity capsuleEntity = new ItemEntity(
                            world,
                            spawnPos.getX() + 0.5 + offsetX,
                            spawnPos.getY() + 0.5,
                            spawnPos.getZ() + 0.5 + offsetZ,
                            capsule
                    );

                    capsuleEntity.setVelocity(
                            direction.getOffsetX() * 0.2,
                            0.0,
                            direction.getOffsetZ() * 0.2
                    );

                    world.spawnEntity(capsuleEntity);
                    updateLevel(world, pos, state, 0);

                    world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
                return ItemActionResult.SUCCESS;
            }
        }

        return ItemActionResult.FAIL;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return state.get(LEVEL);
    }

    public ItemStack getOutput(PlayerEntity player) {
        if (player == null) {
            System.err.println("Player is null");
            return ItemStack.EMPTY;
        }


        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            System.err.println("Player is not in a server world");
            return ItemStack.EMPTY;
        }

        LootContextParameterSet parameters = new LootContextParameterSet.Builder(serverWorld)
                .add(LootContextParameters.ORIGIN, player.getPos())
                .add(LootContextParameters.THIS_ENTITY, player)
                .build(GACHA_MACHINE_LOOT_CONTEXT);

        Identifier lootTableId = Identifier.of(MOD_ID, "gacha_machine");
        LootTable lootTable = serverWorld.getServer()
                .getReloadableRegistries()
                .getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, lootTableId));


        if (lootTable == null) {
            System.err.println("Loot table not found: " + lootTableId);
            return ItemStack.EMPTY;
        }

        List<ItemStack> generatedLoot = lootTable.generateLoot(parameters);

        if (generatedLoot == null || generatedLoot.isEmpty()) {
            System.err.println("No loot generated from table: " + lootTableId);
            return ItemStack.EMPTY;
        }

        return generatedLoot.getFirst();
    }

    private void updateLevel(World world, BlockPos pos, BlockState state, int newLevel) {
        DoubleBlockHalf half = state.get(HALF);
        BlockPos otherHalfPos = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
        BlockState otherHalfState = world.getBlockState(otherHalfPos);

        if (otherHalfState.isOf(this) && otherHalfState.get(HALF) != half) {
            world.setBlockState(pos, state.with(LEVEL, newLevel), 3);
            world.setBlockState(otherHalfPos, otherHalfState.with(LEVEL, newLevel), 3);
        }
    }

}
