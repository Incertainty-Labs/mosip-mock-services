package io.mosip.mock.sbi.stream;

/**
 * Paces the face live stream at {@code mosip.mock.sbi.face.stream.fps} frames per second (1 to 60, default 20,
 * which stays above the 15 fps that liveness needs for blink detection).
 *
 * <p>Frames follow a fixed schedule from {@link #start(long)}, so a sleep that overshoots (Windows rounds sleeps up
 * to 15.6 ms timer ticks) is made up on the next frame and the average rate holds. Not thread-safe: one per stream.
 */
public final class FramePacer {

	/** Frame rate used when the setting is missing or invalid. */
	public static final int DEFAULT_FPS = 20;

	private final int fps;
	private final long intervalMs;
	private long nextDueMs;

	private FramePacer(int fps) {
		this.fps = fps;
		this.intervalMs = 1000L / fps;
	}

	/**
	 * @param value the configured frame rate; {@code null}, non-integer or outside 1 to 60 gives {@link #DEFAULT_FPS}
	 * @return the pacer
	 */
	public static FramePacer fromConfig(String value) {
		if (value != null && value.trim().matches("\\d{1,2}")) {
			int fps = Integer.parseInt(value.trim());
			if (fps >= 1 && fps <= 60) {
				return new FramePacer(fps);
			}
		}
		return new FramePacer(DEFAULT_FPS);
	}

	/** @return frames per second */
	public int fps() {
		return fps;
	}

	/** @param nowMs when the stream's first frame is started */
	public void start(long nowMs) {
		nextDueMs = nowMs + intervalMs;
	}

	/**
	 * Call once after each frame is sent.
	 *
	 * @param nowMs the current time
	 * @return how long to wait until the next frame is due; never negative. A stream more than one interval behind
	 *         starts a new schedule instead of sending a burst of frames.
	 */
	public long waitMillis(long nowMs) {
		if (nowMs - nextDueMs >= intervalMs) {
			nextDueMs = nowMs;
		}
		long wait = Math.max(0, nextDueMs - nowMs);
		nextDueMs += intervalMs;
		return wait;
	}
}
