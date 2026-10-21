package io.mosip.mock.sbi.devicehelper.face;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.mosip.mock.sbi.SBIConstant;

class SBIFaceHelperCertificationTest {

	private final SBIFaceHelper helper = SBIFaceHelper.getInstance(4501, SBIConstant.PURPOSE_REGISTRATION, null,
			"JP2000");

	@ParameterizedTest
	@ValueSource(strings = { "L1", "l1", " L2 " })
	void configuredLevelIsReportedInDiscoveryAndDeviceInfo(String level) {
		helper.setCertification(level);

		helper.initDeviceDetails();

		String expected = level.trim().toUpperCase();
		assertAll(() -> assertEquals(expected, helper.getDiscoverDto().getCertification()),
				() -> assertEquals(expected, helper.getDeviceInfo().getCertification()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "L3", "high", "" })
	void unknownOrBlankLevelKeepsTheLevelFromTheDeviceFiles(String level) {
		helper.setCertification(level);

		helper.initDeviceDetails();

		assertAll(() -> assertEquals("L0", helper.getDiscoverDto().getCertification()),
				() -> assertEquals("L0", helper.getDeviceInfo().getCertification()));
	}

	@Test
	void withoutTheSettingTheDeviceFilesDecide() {
		helper.initDeviceDetails();

		assertEquals("L0", helper.getDiscoverDto().getCertification());
	}
}
