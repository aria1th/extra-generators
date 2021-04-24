package io.github.lucaargolo.extragenerators.common.blockentity

import io.github.lucaargolo.extragenerators.ExtraGenerators
import io.github.lucaargolo.extragenerators.common.block.AbstractGeneratorBlock
import io.github.lucaargolo.extragenerators.utils.ActiveGenerators
import io.github.lucaargolo.extragenerators.utils.ModConfig
import io.github.lucaargolo.extragenerators.utils.SynchronizeableBlockEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.nbt.CompoundTag
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.ItemScatterer
import net.minecraft.util.Tickable
import net.minecraft.util.registry.Registry
import team.reborn.energy.EnergySide
import team.reborn.energy.EnergyStorage
import team.reborn.energy.EnergyTier
import java.util.*

abstract class AbstractGeneratorBlockEntity<B: AbstractGeneratorBlockEntity<B>>(type: BlockEntityType<B>): SynchronizeableBlockEntity(type), Tickable, EnergyStorage {

    var ownerUUID: UUID? = null
    var initialized = false
        private set

    var generatorIdentifier: Identifier? = null
    private var generatorConfig: ModConfig.Generator? = null

    var lastCogWheelRotationDegree = 0f
    var cogWheelRotationDegree = 0f
    var isClientRunning = false
    open fun isRunning() = if(world?.isClient == true) isClientRunning else isServerRunning()
    abstract fun isServerRunning(): Boolean
    abstract fun getCogWheelRotation(): Float

    var storedPower = 0.0
    override fun getMaxStoredPower() = generatorConfig?.storage ?: 0.0
    override fun getTier(): EnergyTier = EnergyTier.INSANE
    override fun getMaxInput(side: EnergySide?) = 0.0
    override fun getMaxOutput(side: EnergySide?) = generatorConfig?.output ?: 0.0
    override fun getStored(face: EnergySide?) = storedPower
    override fun setStored(amount: Double) { storedPower = amount }

    open fun initialize(block: AbstractGeneratorBlock): Boolean {
        (world?.getBlockState(pos)?.block as? AbstractGeneratorBlock)?.let {
            generatorConfig = it.generatorConfig
            generatorIdentifier = Registry.BLOCK.getId(it)
        }
        return generatorIdentifier != null && generatorConfig != null && ownerUUID != null
    }

    override fun tick() {
        if(isClientRunning != isRunning()) {
            isClientRunning = isRunning()
            markDirtyAndSync()
        }
        if(isRunning() && world?.isClient == true) {
            cogWheelRotationDegree += getCogWheelRotation()
            if(cogWheelRotationDegree >= 360f) {
                cogWheelRotationDegree %= 360f
                lastCogWheelRotationDegree -= 360f
            }
            world?.addParticle(ParticleTypes.SMOKE, pos.x+0.5, pos.y+0.825, pos.z+0.5, 0.0, 0.1, 0.0)
        }
        if(isRunning() && world?.isClient == false) {
            if(ownerUUID != null && generatorIdentifier != null && this !is InfiniteGeneratorBlockEntity) {
                ActiveGenerators.add(ownerUUID!!, generatorIdentifier!!)
            }
        }
        if(!initialized) {
            initialized = (world?.getBlockState(pos)?.block as? AbstractGeneratorBlock)?.let { initialize(it) } ?: false
            if(!initialized) {
                ExtraGenerators.LOGGER.error("Failed to initialize generator! This is a bug.")
                (world as? ServerWorld)?.let { serverWorld ->
                    val stacks = Block.getDroppedStacks(cachedState, serverWorld, pos, this)
                    stacks.forEach {
                        ItemScatterer.spawn(serverWorld, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), it)
                    }
                }
                world?.setBlockState(pos, Blocks.AIR.defaultState)
            }
        }
    }

    override fun toTag(tag: CompoundTag): CompoundTag {
        ownerUUID?.let { tag.putUuid("ownerUUID", it) }
        tag.putDouble("storedPower", storedPower)
        return super.toTag(tag)
    }

    override fun fromTag(state: BlockState?, tag: CompoundTag) {
        super.fromTag(state, tag)
        storedPower = tag.getDouble("storedPower")
        if(tag.contains("ownerUUID")) {
            ownerUUID = tag.getUuid("ownerUUID")
        }
    }

    override fun toClientTag(tag: CompoundTag): CompoundTag {
        ownerUUID?.let { tag.putUuid("ownerUUID", it) }
        tag.putDouble("storedPower", storedPower)
        tag.putBoolean("isClientRunning", isClientRunning)
        return tag
    }

    override fun fromClientTag(tag: CompoundTag) {
        storedPower = tag.getDouble("storedPower")
        isClientRunning = tag.getBoolean("isClientRunning")
        if(tag.contains("ownerUUID")) {
            ownerUUID = tag.getUuid("ownerUUID")
        }
    }

}