package tools.data.output;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.ByteOrder;

public class LittleEndianByteBufAllocator extends AbstractByteBufAllocator {

	private final ByteBufAllocator wrapped;

	public LittleEndianByteBufAllocator(ByteBufAllocator wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
		return wrapped.heapBuffer(initialCapacity, maxCapacity).order(ByteOrder.LITTLE_ENDIAN);
	}

	@Override
	protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
		return wrapped.directBuffer(initialCapacity, maxCapacity).order(ByteOrder.LITTLE_ENDIAN);
	}

	@Override
	public boolean isDirectBufferPooled() {
		return false;
	}
}