package com.hollingsworth.craftedmagic.spell.effect;

import com.hollingsworth.craftedmagic.ModConfig;
import com.hollingsworth.craftedmagic.api.spell.AbstractAugment;
import com.hollingsworth.craftedmagic.api.spell.AbstractEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;

public class EffectPull extends AbstractEffect {

    public EffectPull() {
        super(ModConfig.EffectPullID, "Pull");
    }

    @Override
    public void onResolve(RayTraceResult rayTraceResult, World world, LivingEntity shooter, ArrayList<AbstractAugment> augments) {
        if(rayTraceResult instanceof EntityRayTraceResult){
            Entity target = ((EntityRayTraceResult) rayTraceResult).getEntity();
            System.out.println(target);
            Vec3d vec3d = new Vec3d(shooter.posX - target.posX, shooter.posY - target.posY, shooter.posZ - target.posZ);
            double d1 = 7;

            double d2 = 1.0D + 0.5 * getAmplificationBonus(augments);
            //target.setMotion(target.getMotion().add(vec3d.normalize().scale(d2 * d2 * 0.1D)));
            target.setMotion(target.getMotion().add(vec3d.normalize().scale(d2 )));
            target.move(MoverType.SELF, target.getMotion());
        }
    }

    @Override
    public boolean dampenIsAllowed() {
        return true;
    }

    @Override
    public int getManaCost() {
        return 15;
    }
}