package jp.cccis.marun.lib.configure;

public class IllegalConfigurationException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = -6563090909920050908L;

	IllegalConfigurationException(final String message, final Object... params) {
		super(params.length == 0 ? message : String.format(message, params));
	}
}
