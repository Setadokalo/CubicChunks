/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common;

import javax.annotation.ParametersAreNonnullByDefault;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.BlockPortal;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mixin(BlockPortal.Size.class)
public abstract class MixinBlockPortal_Size_HeightLimits {

	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 0, ordinal = 0, expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO), require = 1)
	private int portalSizeClassInitReplace0(int posY, World worldIn, BlockPos origin, EnumFacing.Axis axis) {
		return ((ICubicWorld)worldIn).getMinHeight();
	}
}
