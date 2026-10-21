package io.mosip.mock.sbi.stream;

import java.util.ArrayList;
import java.util.List;

/** Plays a fixed list of JPEG frames in order, starting again after the last one. */
final class LoopingFrameProvider implements FrameProvider {

	private final List<byte[]> frames;
	private int next;

	LoopingFrameProvider(List<byte[]> frames) {
		this.frames = new ArrayList<>(frames);
	}

	@Override
	public synchronized byte[] nextFrame() {
		byte[] frame = frames.get(next);
		next = (next + 1) % frames.size();
		return frame;
	}

	@Override
	public void close() {
		// frames are held in memory; nothing to release
	}
}
