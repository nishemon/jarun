package jp.cccis.marun.lib.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;

import org.apache.ivy.core.module.id.ModuleRevisionId;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;

import jp.cccis.marun.lib.Retriever;
import jp.cccis.marun.lib.configure.Configuration;

public class Setup {
	public static void main(final String... args)
			throws ParseException, IOException {
		JsonReader reader = new JsonReader(new InputStreamReader(System.in));
		GsonBuilder builder = new GsonBuilder();
		JsonSerializer<File> toString = (s, t, c) -> new JsonPrimitive(s.toString());
		builder.registerTypeAdapter(File.class, toString);
		Gson gson = builder.create();
		Configuration config = gson.fromJson(reader, Configuration.class);
		Retriever retriever = Configuration.build(config);
		String[] cols = args[1].split(":");
		ModuleRevisionId rootId = Retriever.makeRevision(cols[0], cols[1], cols[2]);
		gson.toJson(retriever.marun(rootId, args[0]), System.out);
	}
}
