package jp.cccis.marun.repository;

import org.apache.ivy.util.AbstractMessageLogger;
import org.apache.ivy.util.Message;

public class SyserrMessageLogger extends AbstractMessageLogger {
	private int level = Message.MSG_INFO;

	/**
	 * @param level
	 */
	public SyserrMessageLogger(final int level) {
		this.level = level;
	}

	@Override
	public void log(final String msg, final int level) {
		if (level <= this.level) {
			System.err.println(msg);
		}
	}

	@Override
	public void rawlog(final String msg, final int level) {
		log(msg, level);
	}

	@Override
	public void doProgress() {
		System.err.print(".");
	}

	@Override
	public void doEndProgress(final String msg) {
		System.err.println(msg);
	}

	public int getLevel() {
		return this.level;
	}
}
