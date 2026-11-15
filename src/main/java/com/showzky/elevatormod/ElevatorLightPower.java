package com.showzky.elevatormod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Draws one FE per light per second from the connected pulley. */
public final class ElevatorLightPower {
    private static final int FE_PER_LIGHT_PER_SECOND=1;
    public static void tick(PoweredPulleyBlockEntity pulley) {
        Level level=pulley.getLevel();if(level==null || level.isClientSide || pulley.attachment()==null)return;
        var motion=pulley.motion;
        if(motion.active()) {
            boolean changed=false;
            for(int i=0;i<motion.parts.size();i++) {
                var part=motion.parts.get(i);if(!part.state().is(ElevatorRegistries.LIGHT))continue;
                boolean lit=pulley.energy().getEnergyStored()>=FE_PER_LIGHT_PER_SECOND;
                if(lit)pulley.consume(FE_PER_LIGHT_PER_SECOND);
                if(part.state().getValue(ElevatorLightBlock.LIT)!=lit) {
                    motion.parts.set(i,new ElevatorCabinMotion.Part(part.offset(),part.state().setValue(ElevatorLightBlock.LIT,lit),part.data()));changed=true;
                }
            }
            if(changed)pulley.sync();
        } else {
            var scan=ElevatorAssembly.scan(level,pulley.getBlockPos());
            if(scan.attachment()==null || !scan.attachment().equals(pulley.attachment()))return;
            for(BlockPos pos:scan.components()) {
                var state=level.getBlockState(pos);if(!state.is(ElevatorRegistries.LIGHT))continue;
                boolean lit=pulley.energy().getEnergyStored()>=FE_PER_LIGHT_PER_SECOND;
                if(lit)pulley.consume(FE_PER_LIGHT_PER_SECOND);
                if(state.getValue(ElevatorLightBlock.LIT)!=lit)level.setBlock(pos,state.setValue(ElevatorLightBlock.LIT,lit),3);
            }
        }
    }
    public static void extinguishParked(PoweredPulleyBlockEntity pulley) {
        Level level=pulley.getLevel();
        if(level==null || level.isClientSide || pulley.motion.active() || pulley.attachment()==null)return;
        var scan=ElevatorAssembly.scan(level,pulley.getBlockPos());
        if(scan.attachment()==null || !scan.attachment().equals(pulley.attachment()))return;
        for(BlockPos pos:scan.components()) {
            var state=level.getBlockState(pos);
            if(state.is(ElevatorRegistries.LIGHT) && state.getValue(ElevatorLightBlock.LIT))level.setBlock(pos,state.setValue(ElevatorLightBlock.LIT,false),3);
        }
    }
    private ElevatorLightPower() {}
}
