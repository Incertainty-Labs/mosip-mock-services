package io.mosip.mock.sbi.stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FramePacerTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "fast", "0", "61", "-5", "12.5" })
	void unsetOrInvalidFpsUsesTwenty(String value) {
		assertEquals(20, FramePacer.fromConfig(value).fps());
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", " 15 ", "60" })
	void fpsBetweenOneAndSixtyIsKept(String value) {
		assertEquals(Integer.parseInt(value.trim()), FramePacer.fromConfig(value).fps());
	}

	@Test
	void waitsUntilTheNextFrameIsDue() {
		FramePacer pacer = FramePacer.fromConfig("20");
		pacer.start(1_000);

		assertAll(() -> assertEquals(46, pacer.waitMillis(1_004)),
				() -> assertEquals(50, pacer.waitMillis(1_050)));
	}

	@Test
	void aLateFrameIsMadeUpOnTheNextOneSoTheRateHolds() {
		FramePacer pacer = FramePacer.fromConfig("20");
		pacer.start(1_000);

		// the sleep overshot by 12 ms (Windows timer ticks are 15.6 ms)
		assertEquals(46, pacer.waitMillis(1_004));
		assertEquals(37, pacer.waitMillis(1_063));
	}

	@Test
	void fallingFarBehindRestartsTheScheduleInsteadOfBursting() {
		FramePacer pacer = FramePacer.fromConfig("20");
		pacer.start(1_000);

		assertAll(() -> assertEquals(0, pacer.waitMillis(1_300)),
				() -> assertEquals(45, pacer.waitMillis(1_305)));
	}
}
