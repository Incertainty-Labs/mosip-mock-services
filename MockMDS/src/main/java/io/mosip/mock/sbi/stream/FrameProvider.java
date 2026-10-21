package io.mosip.mock.sbi.stream;

import java.io.Closeable;
import java.io.IOException;

/** Supplies the face device's live stream, one JPEG frame at a time. */
public interface FrameProvider extends Closeable {

	/**
	 * @return the next frame as JPEG bytes; file sources loop forever
	 * @throws IOException if the source can no longer give frames
	 */
	byte[] nextFrame() throws IOException;
}
