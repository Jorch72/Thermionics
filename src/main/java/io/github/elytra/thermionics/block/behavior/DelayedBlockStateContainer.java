package io.github.elytra.thermionics.block.behavior;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.Validate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.github.elytra.thermionics.block.BlockImpl;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.MathHelper;

public class DelayedBlockStateContainer extends BlockStateContainer {
	private boolean frozen = false;
	
	private Block block;
	private Map<String, PropertyValueMap<?>> values = new HashMap<>();
	
	private BlockStateContainer delegate = null;
	
	public DelayedBlockStateContainer(Block block) {
		super(block);
		this.block = block;
	}

	/* DELAYED MEMES */
	
	public <T extends Comparable<T>> DelayedBlockStateContainer add(IProperty<T> property) {
		if (frozen) throw new IllegalStateException("Tried to add property "+property.getName()+" to an immutable BlockStateContainer.");
		validateProperty(this.getBlock(), property);
		//properties.put(property.getName(), property);
		values.put(property.getName(), new PropertyValueMap<>(property));
		return this;
	}

	public DelayedBlockStateContainer freeze(BlockStateBehavior behavior) {
		frozen = true;
		if (behavior!=null) {
			delegate = behavior.createBlockState(block);
		} else {
			values = ImmutableMap.copyOf(values);
			IProperty<?>[] properties = new IProperty<?>[values.size()];
			int i = 0;
			for(PropertyValueMap<?> map : values.values()) {
				properties[i] = map.getKey();
				i++;
			}
			delegate = new BlockStateContainer(block, properties);
		}
		
		return this;
	}
	
	/* IMPL / PASSTHROUGH */
	
	public ImmutableList<IBlockState> getValidStates() {
		if (delegate==null) return ImmutableList.of();
		return delegate.getValidStates();
	}

	public IBlockState getBaseState() {
		if (delegate==null) return super.getBaseState();
		//if (delegate==null) return null;
		//Validate.notNull(delegate, "Cannot get the base state of an unbuilt BlockStateContainer.");
		return delegate.getBaseState();
	}

	public Block getBlock() {
		return block;
	}

	public Collection<IProperty<?>> getProperties() {
		return ImmutableList.copyOf(
				values
				.values()
				.stream()
				.map(it->it.getKey())
				.iterator());
	}

	public String toString() {
		return "{block:\""+Block.REGISTRY.getNameForObject(this.block)+"\" properties:"
				+this.values.keySet()+"}";
	}

	@Nullable
	public IProperty<?> getProperty(String propertyName) {
		return values.get(propertyName).getKey();
	}
	
	/* BITFIELD LOGIC */
	
	public static class PropertyValueMap<T extends Comparable<T>> {
		private int bitOffset = 0;
		private int bitWidth = 0;
		private int bitMask = 0x0;
		BitSet bits;
		
		private IProperty<T> property;
		private T[] values;
		
		@SuppressWarnings("unchecked")
		public PropertyValueMap(IProperty<T> property) {
			this.property = property;
			values = (T[])property.getAllowedValues().toArray();
			bitWidth = (int)Math.ceil(MathHelper.log2(values.length));
			setBitOffset(0);
		}
		
		public void setBitOffset(int offset) {
			bitOffset = offset;
			bitMask = 0x0;
			for(int i=0; i<bitWidth; i++) {
				bitMask = bitMask << 1;
				bitMask |= 1;
			}
		}
		
		public int width() {
			return bitWidth;
		}
		
		@Nonnull
		public IProperty<T> getKey() {
			return property;
		}
		
		@Nullable
		public T getValue(long bits) {
			int index = (int)((bits >> bitOffset) & bitMask);
			if (index>=values.length) return null;
			return values[index];
		}
		
		@Nullable
		public T getValue(@Nonnull BitSet bits) {
			int index = (int) longValue(bits.get(bitOffset, bitOffset+bitWidth));
			if (index>=values.length) return null;
			return values[index];
			
		}
		
		/** Returns the lowest-order long in a BitSet **/
		private static long longValue(@Nonnull BitSet bits) {
			if (bits.length()>64) return Long.MAX_VALUE;
			return bits.toLongArray()[0];
		}
	}
}