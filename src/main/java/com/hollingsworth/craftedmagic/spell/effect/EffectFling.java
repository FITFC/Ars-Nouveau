package com.hollingsworth.craftedmagic.spell.effect;

import com.hollingsworth.craftedmagic.ModConfig;
import com.hollingsworth.craftedmagic.api.spell.AbstractAugment;
import com.hollingsworth.craftedmagic.api.spell.AbstractEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.ArrayList;

public class EffectFling extends AbstractEffect {
    public EffectFling() {
        super(ModConfig.EffectFlingID, "Fling");
    }

    @Override
    public void onResolve(RayTraceResult rayTraceResult, World world, LivingEntity shooter, ArrayList<AbstractAugment> augments) {
        if(rayTraceResult instanceof EntityRayTraceResult){
            Entity entity = ((EntityRayTraceResult) rayTraceResult).getEntity();
            entity.setMotion(entity.getMotion().add(0, 0.8 + getAmplificationBonus(augments), 0));
            entity.velocityChanged = true;
        }
    }

    @Override
    public boolean dampenIsAllowed() {
        return true;
    }

    @Override
    public int getManaCost() {
        return 20;
    }


}