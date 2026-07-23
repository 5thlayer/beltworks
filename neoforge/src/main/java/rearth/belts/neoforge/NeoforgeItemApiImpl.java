package rearth.belts.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;
import rearth.belts.Belts;
import rearth.belts.api.item.BlockItemApi;
import rearth.belts.api.item.ItemApi;

public class NeoforgeItemApiImpl implements BlockItemApi {

    @Override
    public ItemApi.InventoryStorage find(
            Level level,
            BlockPos pos,
            @Nullable BlockState state,
            @Nullable BlockEntity entity,
            @Nullable Direction direction
    ) {
        var candidate = level.getCapability(Capabilities.Item.BLOCK, pos, state, entity, direction);
        return candidate == null ? null : new NeoForgeStorageWrapper(candidate);
    }

    public static class NeoForgeStorageWrapper implements ItemApi.InventoryStorage {

        private final ResourceHandler<ItemResource> storage;

        public NeoForgeStorageWrapper(ResourceHandler<ItemResource> storage) {
            this.storage = storage;
        }

        @Override
        public int insert(ItemStack inserted, boolean simulate) {
            if (inserted.isEmpty()) return 0;
            try (var transaction = Transaction.openRoot()) {
                var amount = storage.insert(ItemResource.of(inserted), inserted.getCount(), transaction);
                if (!simulate) transaction.commit();
                return amount;
            }
        }

        @Override
        public int insertToSlot(ItemStack inserted, int slot, boolean simulate) {
            if (inserted.isEmpty()) return 0;
            try (var transaction = Transaction.openRoot()) {
                var amount = storage.insert(slot, ItemResource.of(inserted), inserted.getCount(), transaction);
                if (!simulate) transaction.commit();
                return amount;
            }
        }

        @Override
        public int extract(ItemStack extracted, boolean simulate) {
            if (extracted.isEmpty()) return 0;
            try (var transaction = Transaction.openRoot()) {
                var amount = storage.extract(ItemResource.of(extracted), extracted.getCount(), transaction);
                if (!simulate) transaction.commit();
                return amount;
            }
        }

        @Override
        public int extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
            if (extracted.isEmpty()) return 0;
            try (var transaction = Transaction.openRoot()) {
                var amount = storage.extract(slot, ItemResource.of(extracted), extracted.getCount(), transaction);
                if (!simulate) transaction.commit();
                return amount;
            }
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            try (var transaction = Transaction.openRoot()) {
                var current = storage.getResource(slot);
                if (!current.isEmpty()) {
                    storage.extract(slot, current, storage.getAmountAsInt(slot), transaction);
                }
                if (!stack.isEmpty()) {
                    storage.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
                }
                transaction.commit();
            } catch (RuntimeException exception) {
                Belts.LOGGER.error("Unable to set stack in slot {}, stack is {}", slot, stack, exception);
            }
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return storage.getResource(slot).toStack(storage.getAmountAsInt(slot));
        }

        @Override
        public int getSlotCount() {
            return storage.size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return storage.getCapacityAsInt(slot, storage.getResource(slot));
        }
    }
}
