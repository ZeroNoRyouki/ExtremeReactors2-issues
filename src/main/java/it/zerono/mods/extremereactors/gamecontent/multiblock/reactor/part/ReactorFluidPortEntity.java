/*
 *
 * ReactorFluidPortEntity.java
 *
 * This file is part of Extreme Reactors 2 by ZeroNoRyouki, a Minecraft mod.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * DO NOT REMOVE OR EDIT THIS HEADER
 *
 */

package it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part;

import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.fluidport.FluidPortType;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.fluidport.IFluidPort;
import it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.fluidport.IFluidPortHandler;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.variant.IMultiblockReactorVariant;
import it.zerono.mods.zerocore.lib.block.INeighborChangeListener;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.data.IoMode;
import it.zerono.mods.zerocore.lib.fluid.FluidHelper;
import it.zerono.mods.zerocore.lib.multiblock.ITickableMultiblockPart;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ReactorFluidPortEntity
        extends AbstractReactorEntity
        implements IFluidPort<MultiblockReactor, IMultiblockReactorVariant>, ITickableMultiblockPart, INeighborChangeListener {

    public ReactorFluidPortEntity(final FluidPortType type, final IoMode mode, final TileEntityType<?> entityType) {

        super(entityType);
        this._handler = IFluidPortHandler.create(type, mode, this);
        this.setIoDirection(IoDirection.Input);
    }

    //region IFluidPort

    @Override
    public IFluidPortHandler<MultiblockReactor, IMultiblockReactorVariant> getFluidPortHandler() {
        return this._handler;
    }

    //endregion
    //region INeighborChangeListener

    /**
     * Called when a neighboring Block on a side of this TileEntity changes
     *
     * @param state the BlockState of this TileEntity block
     * @param neighborPosition position of neighbor
     * @param isMoving ?
     */
    @Override
    public void onNeighborBlockChanged(final BlockState state, final BlockPos neighborPosition, final boolean isMoving) {

        if (this.isConnected()) {
            this.getFluidPortHandler().checkConnections(this.getWorld(), this.getWorldPosition());
        }

        this.requestClientRenderUpdate();
    }

    /**
     * Called when a neighboring TileEntity on a side of this TileEntity changes, is created or is destroyed
     *
     * @param state the BlockState of this TileEntity block
     * @param neighborPosition position of neighbor
     */
    @Override
    public void onNeighborTileChanged(final BlockState state, final BlockPos neighborPosition) {

        if (this.isConnected()) {
            this.getFluidPortHandler().checkConnections(this.getWorld(), this.getWorldPosition());
        }

        this.requestClientRenderUpdate();
    }

    //endregion
    //region IIoEntity

    @Override
    public IoDirection getIoDirection() {
        return this._direction;
    }

    @Override
    public void setIoDirection(IoDirection direction) {

        if (this.getIoDirection() == direction) {
            return;
        }

        this._direction = direction;
        this.getFluidPortHandler().update();
        this.executeOnController(MultiblockReactor::onFluidPortChanged);
        this.notifyBlockUpdate();

        this.callOnLogicalSide(
                () -> {
                    this.notifyOutwardNeighborsOfStateChange();
                    this.markDirty();
                },
                this::markForRenderUpdate
        );
    }

    //endregion
    //region ITickableMultiblockPart

    /**
     * Called once every tick from the multiblock server-side tick loop.
     */
    @Override
    public void onMultiblockServerTick() {

        if (this.getIoDirection().isOutput()) {
            this.getOutwardDirection().ifPresent(this::transferGas);
        }
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundNBT data, final SyncReason syncReason) {

        super.syncDataFrom(data, syncReason);
        this.setIoDirection(IoDirection.read(data, "iodir", IoDirection.Input));
    }

    @Override
    public CompoundNBT syncDataTo(final CompoundNBT data, final SyncReason syncReason) {

        super.syncDataTo(data, syncReason);
        IoDirection.write(data, "iodir", this.getIoDirection());
        return data;
    }

    //endregion
    //region client render support

    @Override
    protected int getUpdatedModelVariantIndex() {

        if (this.isMachineAssembled()) {

            final int connectedOffset = this.getFluidPortHandler().isConnected() ? 1 : 0;

            return this.getIoDirection().isInput() ? 0 + connectedOffset : 2 + connectedOffset;

        } else {

            return 0;
        }
    }

    //endregion
    //region AbstractCuboidMultiblockPart

    @Override
    public void onPostMachineAssembled(MultiblockReactor controller) {

        super.onPostMachineAssembled(controller);
        this.getFluidPortHandler().update();
        this.notifyOutwardNeighborsOfStateChange();
    }

    @Override
    public void onPostMachineBroken() {

        super.onPostMachineBroken();
        this.getFluidPortHandler().update();
        this.notifyOutwardNeighborsOfStateChange();
    }

    @Override
    public void onAttached(MultiblockReactor newController) {

        super.onAttached(newController);
//        this.updateCapabilityForwarder();
        this.getFluidPortHandler().update();
    }

    @Override
    public void onAssimilated(MultiblockReactor newController) {

        super.onAssimilated(newController);
//        this.updateCapabilityForwarder();
        this.getFluidPortHandler().update();
    }

    @Override
    public void onDetached(MultiblockReactor oldController) {

        super.onDetached(oldController);
//        this.updateCapabilityForwarder();
        this.getFluidPortHandler().update();
    }

    //endregion
    //region TileEntity

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {

        final LazyOptional<T> cap = this.getFluidPortHandler().getCapability(capability, side);

        return null != cap ? cap : super.getCapability(capability, side);
    }

    /**
     * invalidates a tile entity
     */
    @Override
    public void remove() {

        super.remove();
        this.getFluidPortHandler().invalidate();
//        this._fluidCapability.invalidate();
    }

    //endregion
    //region internals

    private void transferGas(final Direction direction) {

        final BlockPos targetPosition = this.getWorldPosition().offset(direction);

        this.getMultiblockController()
                .flatMap(MultiblockReactor::getGasHandler)
                .ifPresent(source -> FluidHelper.tryFluidTransfer(source, this.getPartWorldOrFail(), targetPosition,
                        direction.getOpposite(), Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE));
    }

    private final IFluidPortHandler<MultiblockReactor, IMultiblockReactorVariant> _handler;
    private IoDirection _direction;

    //endregion
}
