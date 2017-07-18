package jp.cccis.marun.lib.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ivy.Ivy;

import com.google.gson.Gson;

public class Health {
	public static void main(final String... args) {
		Map<String, Object> ret = new LinkedHashMap<>();
		ret.put("ivy", Ivy.getIvyVersion());
		ret.put("java", System.getProperty("java.version"));
		new Gson().toJson(ret, System.out);
	}
}
