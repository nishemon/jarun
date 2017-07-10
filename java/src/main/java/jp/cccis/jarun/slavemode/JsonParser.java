package jp.cccis.jarun.slavemode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import jp.cccis.jarun.configure.Configuration;
import jp.cccis.jarun.configure.Repository;

public class JsonParser {

	public static Configuration parse(final InputStream in, final Class<Configuration> clazz)
			throws ScriptException, NoSuchMethodException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			StringBuilder builder = new StringBuilder(1024);
			br.lines().forEach(builder::append);
			ScriptEngineManager manager = new ScriptEngineManager();
			ScriptEngine engine = manager.getEngineByName("JavaScript");
			InputStream is = JsonParser.class.getResourceAsStream("JsonParser.js");
			engine.eval(new InputStreamReader(is, StandardCharsets.UTF_8));
			Invocable invocable = (Invocable) engine;
			return (Configuration) invocable.invokeFunction("convert", builder.toString(), clazz.getName());
		} catch (IOException e) {
			// log.error("unknown exception", e);
			e.printStackTrace();
		}
	}

	public static void main(final String[] args) throws ScriptException, NoSuchMethodException {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		InputStream is = JsonParser.class.getResourceAsStream("JsonParser.js");
		engine.eval(new InputStreamReader(is, StandardCharsets.UTF_8));
		Invocable invocable = (Invocable) engine;
		Object ret = invocable.invokeFunction("convert", "{\"baeurl\":\"http://cccis.jp/\"}",
				Repository.class.getName());
		System.out.println(ret);
	}
}
