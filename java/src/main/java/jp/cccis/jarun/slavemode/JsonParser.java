package jp.cccis.jarun.slavemode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
			InputStream jsr = JsonParser.class.getClassLoader().getResourceAsStream("JsonParser.js");
			engine.eval(new InputStreamReader(jsr, StandardCharsets.UTF_8));
			Invocable invocable = (Invocable) engine;
			String json = builder.toString();
			Configuration c = (Configuration) invocable.invokeFunction("convert", json, clazz.getName());
			@SuppressWarnings("unchecked")
			List<Repository> repositories = (List<Repository>) invocable.invokeFunction("convertArray", json,
					"repositories",
					Repository.class.getName());
			c.setRepositories(repositories);
			return c;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void main(final String[] args) throws ScriptException, NoSuchMethodException {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		InputStream is = JsonParser.class.getClassLoader().getResourceAsStream("JsonParser.js");
		engine.eval(new InputStreamReader(is, StandardCharsets.UTF_8));
		Invocable invocable = (Invocable) engine;
		Object ret = invocable.invokeFunction("convert", "{\"baeurl\":\"http://cccis.jp/\"}",
				Repository.class.getName());
		System.out.println(ret);
	}
}
