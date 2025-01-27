package com.hollingsworth.arsnouveau.api.spell;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.event.EffectResolveEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.perk.IEffectResolvePerk;
import com.hollingsworth.arsnouveau.api.perk.PerkInstance;
import com.hollingsworth.arsnouveau.api.util.CuriosUtil;
import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.common.capability.CapabilityRegistry;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.hollingsworth.arsnouveau.api.util.ManaUtil.getPlayerDiscounts;

public class SpellResolver {
    public AbstractCastMethod castType;
    public Spell spell;
    public SpellContext spellContext;
    public boolean silent;
    private final ISpellValidator spellValidator;

    public @Nullable HitResult hitResult = null;

    public SpellResolver(SpellContext spellContext) {
        this.spell = spellContext.getSpell();
        this.castType = spellContext.getSpell().getCastMethod();
        this.spellContext = spellContext;
        this.spellValidator = ArsNouveauAPI.getInstance().getSpellCastingSpellValidator();
    }

    public SpellResolver withSilent(boolean isSilent) {
        this.silent = isSilent;
        return this;
    }

    public boolean canCast(LivingEntity entity) {
        // Validate the spell
        List<SpellValidationError> validationErrors = spellValidator.validate(spell.recipe);

        if (validationErrors.isEmpty()) {
            // Validation successful. We can check the player's mana now.
            return enoughMana(entity);
        } else {
            // Validation failed, explain why if applicable
            if (!silent && !entity.getCommandSenderWorld().isClientSide) {
                // Sending only the first error to avoid spam
                PortUtil.sendMessageNoSpam(entity, validationErrors.get(0).makeTextComponentExisting());
            }
            return false;
        }
    }

    boolean enoughMana(LivingEntity entity) {
        int totalCost = getResolveCost();
        IManaCap manaCap = CapabilityRegistry.getMana(entity).orElse(null);
        if (manaCap == null)
            return false;
        boolean canCast = totalCost <= manaCap.getCurrentMana() || (entity instanceof Player player && player.isCreative());
        if (!canCast && !entity.getCommandSenderWorld().isClientSide && !silent)
            PortUtil.sendMessageNoSpam(entity, Component.translatable("ars_nouveau.spell.no_mana"));
        return canCast;
    }

    public boolean postEvent() {
        return SpellUtil.postEvent(new SpellCastEvent(spell, spellContext));
    }

    private SpellStats getCastStats() {
        LivingEntity caster = spellContext.getUnwrappedCaster();
        return new SpellStats.Builder()
                .setAugments(spell.getAugments(0, caster))
                .addItemsFromEntity(caster)
                .build(castType, this.hitResult, caster.level, caster, spellContext);
    }

    public boolean onCast(ItemStack stack, Level level) {
        if (canCast(spellContext.getUnwrappedCaster()) && !postEvent()) {
            this.hitResult = null;
            CastResolveType resolveType = castType.onCast(stack, spellContext.getUnwrappedCaster(), level, getCastStats(), spellContext, this);
            if (resolveType == CastResolveType.SUCCESS) {
                expendMana();
            }
            return resolveType.wasSuccess;
        }
        return false;
    }

    public boolean onCastOnBlock(BlockHitResult blockRayTraceResult) {
        if (canCast(spellContext.getUnwrappedCaster()) && !postEvent()) {
            this.hitResult = blockRayTraceResult;
            CastResolveType resolveType = castType.onCastOnBlock(blockRayTraceResult, spellContext.getUnwrappedCaster(), getCastStats(), spellContext, this);
            if (resolveType == CastResolveType.SUCCESS) {
                expendMana();
            }
            return resolveType.wasSuccess;
        }
        return false;
    }

    // Gives context for InteractionHand
    public boolean onCastOnBlock(UseOnContext context) {
        if (canCast(spellContext.getUnwrappedCaster()) && !postEvent()) {
            this.hitResult = context.hitResult;
            CastResolveType resolveType = castType.onCastOnBlock(context, getCastStats(), spellContext, this);
            if (resolveType == CastResolveType.SUCCESS) {
                expendMana();
            }
            return resolveType.wasSuccess;
        }
        return false;
    }

    public boolean onCastOnEntity(ItemStack stack, Entity target, InteractionHand hand) {
        if (canCast(spellContext.getUnwrappedCaster()) && !postEvent()) {
            this.hitResult = new EntityHitResult(target);
            CastResolveType resolveType = castType.onCastOnEntity(stack, spellContext.getUnwrappedCaster(), target, hand, getCastStats(), spellContext, this);
            if (resolveType == CastResolveType.SUCCESS) {
                expendMana();
            }
            return resolveType.wasSuccess;
        }
        return false;
    }

    public void onResolveEffect(Level world, HitResult result) {
        this.hitResult = result;
        this.resolveAllEffects(world);
    }

    protected void resolveAllEffects(Level world) {
        spellContext.resetCastCounter();
        LivingEntity shooter = spellContext.getUnwrappedCaster();
        SpellResolveEvent.Pre spellResolveEvent = new SpellResolveEvent.Pre(world, shooter, this.hitResult, spell, spellContext, this);
        MinecraftForge.EVENT_BUS.post(spellResolveEvent);
        if (spellResolveEvent.isCanceled())
            return;
        List<PerkInstance> perkInstances = shooter instanceof Player player ? PerkUtil.getPerksFromPlayer(player) : new ArrayList<>();
        while (spellContext.hasNextPart()) {
            AbstractSpellPart part = spellContext.nextPart();
            if (part == null)
                break;
            if (part instanceof AbstractAugment)
                continue;
            SpellStats.Builder builder = new SpellStats.Builder();
            List<AbstractAugment> augments = spell.getAugments(spellContext.getCurrentIndex() - 1, shooter);
            SpellStats stats = builder
                    .setAugments(augments)
                    .addItemsFromEntity(shooter)
                    .build(part, this.hitResult, world, shooter, spellContext);
            if(!(part instanceof AbstractEffect effect))
                continue;

            EffectResolveEvent.Pre preEvent = new EffectResolveEvent.Pre(world, shooter, this.hitResult, spell, spellContext, effect, stats, this);
            if (MinecraftForge.EVENT_BUS.post(preEvent))
                continue;

            for(PerkInstance perkInstance : perkInstances){
                if(perkInstance.getPerk() instanceof IEffectResolvePerk effectPerk){
                    effectPerk.onPreResolve(this.hitResult, world, shooter, stats, spellContext, this, effect, perkInstance);
                }
            }
            effect.onResolve(this.hitResult, world, shooter, stats, spellContext, this);
            for(PerkInstance perkInstance : perkInstances){
                if(perkInstance.getPerk() instanceof IEffectResolvePerk effectPerk){
                    effectPerk.onPostResolve(this.hitResult, world, shooter, stats, spellContext, this, effect, perkInstance);
                }
            }

            MinecraftForge.EVENT_BUS.post(new EffectResolveEvent.Post(world, shooter, this.hitResult, spell, spellContext, effect, stats, this));
        }

        MinecraftForge.EVENT_BUS.post(new SpellResolveEvent.Post(world, shooter, this.hitResult, spell, spellContext, this));
    }

    public void expendMana() {
        int totalCost = getResolveCost();
        CapabilityRegistry.getMana(spellContext.getUnwrappedCaster()).ifPresent(mana -> mana.removeMana(totalCost));
    }

    public int getResolveCost() {
        int cost = spellContext.getSpell().getFinalCostAndReset() - getPlayerDiscounts(spellContext.getUnwrappedCaster());
        return Math.max(cost, 0);
    }

    /**
     * Addons can override this to return their custom spell resolver if you change the way logic resolves.
     */
    public SpellResolver getNewResolver(SpellContext context) {
        return new SpellResolver(context);
    }

    /**
     * Check if the caster has a focus when modifying glyph behavior.
     * Addons can override this to check other types of casters like turrets or entities.
     */
    public boolean hasFocus(ItemStack stack) {
        return CuriosUtil.hasItem(spellContext.getUnwrappedCaster(), stack);
    }
}
