package net.sourceforge.cmus.droid;

public final class Validator {
	private Validator() {
	}

	static public boolean validateString(String str) {
		return str != null && str.trim().length() != 0;
	}

	static public boolean validateInteger(String str) {
		if (str != null && str.trim().length() != 0) {
			try {
				Integer.parseInt(str);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}
}
