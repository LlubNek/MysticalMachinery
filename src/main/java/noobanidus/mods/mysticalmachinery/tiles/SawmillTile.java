package noobanidus.mods.mysticalmachinery.tiles;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.AbstractCookingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import noobanidus.mods.mysticalmachinery.container.SawmillContainer;
import noobanidus.mods.mysticalmachinery.init.ModRecipes;
import noobanidus.mods.mysticalmachinery.init.ModTiles;
import noobanidus.mods.mysticalmachinery.recipes.LogPlankLoader;

import javax.annotation.Nullable;

@SuppressWarnings("Duplicates")
public class SawmillTile extends AbstractFastFurnaceTileEntity {
  @SuppressWarnings("ConstantConditions")
  public SawmillTile() {
    super(ModTiles.SAWMILL.get(), ModRecipes.SAWMILL_TYPE);
  }

  @Override
  protected ITextComponent getDefaultName() {
    return new TranslationTextComponent("mysticalmachinery.container.sawmill");
  }

  @Override
  protected Container createMenu(int id, PlayerInventory player) {
    return new SawmillContainer(id, player, this, this.furnaceData);
  }

  protected AbstractCookingRecipe curRecipe;
  protected ItemStack failedMatch = ItemStack.EMPTY;

  @Override
  public void tick() {
    boolean wasBurning = this.isBurning();
    boolean dirty = false;
    if (this.isBurning()) {
      --this.burnTime;
    }

    if (!this.world.isRemote) {
      ItemStack fuel = this.items.get(FUEL);
      if (this.isBurning() || !fuel.isEmpty() && !this.items.get(INPUT).isEmpty()) {
        AbstractCookingRecipe irecipe = getRecipe();
        boolean valid = this.canSmelt(irecipe);
        if (!this.isBurning() && (valid || isLog())) {
          this.burnTime = this.getBurnTime(fuel);
          this.recipesUsed = this.burnTime;
          if (this.isBurning()) {
            dirty = true;
            if (fuel.hasContainerItem()) this.items.set(1, fuel.getContainerItem());
            else if (!fuel.isEmpty()) {
              fuel.shrink(1);
              if (fuel.isEmpty()) {
                this.items.set(1, fuel.getContainerItem());
              }
            }
          }
        }

        if (this.isBurning() && (valid || isLog())) {
          ++this.cookTime;
          if (this.cookTime == this.cookTimeTotal) {
            this.cookTime = 0;
            this.cookTimeTotal = this.getCookTime();
            this.smeltItem(irecipe);
            dirty = true;
          }
        } else {
          this.cookTime = 0;
        }
      } else if (!this.isBurning() && this.cookTime > 0) {
        this.cookTime = MathHelper.clamp(this.cookTime - 2, 0, this.cookTimeTotal);
      }

      if (wasBurning != this.isBurning()) {
        dirty = true;
        this.world.setBlockState(this.pos, this.world.getBlockState(this.pos).with(AbstractFurnaceBlock.LIT, this.isBurning()), 3);
      }
    }

    if (dirty) {
      this.markDirty();
    }
  }

  @Override
  protected boolean canSmelt(@Nullable IRecipe<?> recipe) {
    if (!this.items.get(0).isEmpty() && recipe != null) {
      ItemStack recipeOutput = recipe.getRecipeOutput();
      if (!recipeOutput.isEmpty()) {
        ItemStack output = this.items.get(OUTPUT);
        if (output.isEmpty()) return true;
        else if (!output.isItemEqual(recipeOutput)) return false;
        else return output.getCount() + recipeOutput.getCount() <= output.getMaxStackSize();
      }
    }
    return false;
  }

  @Override
  protected void smeltItem(@Nullable IRecipe<?> recipe) {
    if (isLog() || (recipe != null && this.canSmelt(recipe))) {
      ItemStack itemstack = this.items.get(0);
      ItemStack itemstack1 = isLog() ? new ItemStack(LogPlankLoader.getPlank(itemstack.getItem()), 6) : recipe.getRecipeOutput();
      ItemStack itemstack2 = this.items.get(2);
      if (itemstack2.isEmpty()) {
        this.items.set(2, itemstack1.copy());
      } else if (itemstack2.getItem() == itemstack1.getItem()) {
        itemstack2.grow(itemstack1.getCount());
      }

      if (!this.world.isRemote) {
        this.setRecipeUsed(recipe);
      }

      if (itemstack.getItem() == Blocks.WET_SPONGE.asItem() && !this.items.get(1).isEmpty() && this.items.get(1).getItem() == Items.BUCKET) {
        this.items.set(1, new ItemStack(Items.WATER_BUCKET));
      }

      itemstack.shrink(1);
    }
  }

  @Override
  protected int getCookTime() {
    AbstractCookingRecipe rec = getRecipe();
    if (rec == null) return 30;
    return rec.getCookTime();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected AbstractCookingRecipe getRecipe() {
    ItemStack input = this.getStackInSlot(INPUT);
    if (input.isEmpty() || input == failedMatch) return null;
    if (curRecipe != null && curRecipe.matches(this, world)) return curRecipe;
    else {
      AbstractCookingRecipe rec = world.getRecipeManager().getRecipe((IRecipeType<AbstractCookingRecipe>) this.recipeType, this, this.world).orElse(null);
      if (rec == null) failedMatch = input;
      else failedMatch = ItemStack.EMPTY;
      return curRecipe = rec;
    }
  }

  private boolean isLog() {
    ItemStack input = this.getStackInSlot(INPUT);
    if (!input.getItem().isIn(ItemTags.LOGS)) {
      return false;
    }

    Item planks = LogPlankLoader.getPlank(input.getItem());
    if (planks == null) {
      return false;
    }

    return true;
  }
}
