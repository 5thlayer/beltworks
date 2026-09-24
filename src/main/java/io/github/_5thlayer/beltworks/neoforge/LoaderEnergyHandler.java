// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.model.LoaderEnergy;

/**
 * A powered loader's FE face. It takes and never gives. A pole measures a machine's room with an
 * insert it then aborts, so every insert is journalled or the probe's worth stays behind.
 */
public final class LoaderEnergyHandler extends SnapshotJournal<Long> implements EnergyHandler {

    private final BeltEndBlockEntity loader;
    private final LoaderEnergy energy;

    public LoaderEnergyHandler(BeltEndBlockEntity loader) {
        this.loader = loader;
        this.energy = loader.getEnergy();
    }

    @Override
    public long getAmountAsLong() {
        return energy.storedFe();
    }

    @Override
    public long getCapacityAsLong() {
        return energy.capacityFe();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) return 0;
        updateSnapshots(transaction);
        return (int) energy.insertFe(amount);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    protected Long createSnapshot() {
        return energy.joules();
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        energy.setJoules(snapshot);
    }

    @Override
    protected void onRootCommit(Long originalState) {
        if (energy.joules() != originalState) loader.setChanged();
    }
}
