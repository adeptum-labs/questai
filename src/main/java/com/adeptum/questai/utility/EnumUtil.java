package com.adeptum.questai.utility;

import java.security.SecureRandom;

public final class EnumUtil {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private EnumUtil() {
	}

	public static <T extends Enum<?>> T random(Class<T> clazz) {
		int x = SECURE_RANDOM.nextInt(clazz.getEnumConstants().length);
		return clazz.getEnumConstants()[x];
	}
}
