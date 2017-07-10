package jp.cccis.jarun.slavemode;

import java.util.HashMap;
import java.util.Map;

import org.apache.ivy.Ivy;

public class Health {
	public static void main(final String... args) {
		Map<String, Object> ret = new HashMap<>();
		ret.put("ivy", Ivy.getIvyVersion());
		ret.put("java", System.getProperty("java.version"));
	}
}
