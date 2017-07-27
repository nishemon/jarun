package jp.cccis.marun.lib.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ivy.core.module.id.ModuleRevisionId;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;

import jp.cccis.marun.lib.MarunExtractor;
import jp.cccis.marun.lib.MarunOutputReport;
import jp.cccis.marun.lib.MarunOutputReport.JarStatus;
import jp.cccis.marun.lib.Retriever;
import jp.cccis.marun.lib.configure.Configuration;

public class Setup {
	private final MarunExtractor extractor;

	private Setup(final List<JarStatus> jars) throws ClassNotFoundException, IOException {
		List<Path> paths = new ArrayList<>();
		for (JarStatus js : jars) {
			paths.add(js.getPath());
		}
		this.extractor = new MarunExtractor(paths);
	}

	private List<String> findMain() {
		List<Method> methods = this.extractor.findMethod("main", String[].class);
		return methods.stream().map(m -> m.getDeclaringClass().getName()).collect(Collectors.toList());
	}

	public static void main(final String... args)
			throws ParseException, IOException, ClassNotFoundException {
		JsonReader reader = new JsonReader(new InputStreamReader(System.in));
		GsonBuilder builder = new GsonBuilder();
		JsonSerializer<Path> toString = (s, t, c) -> new JsonPrimitive(s.toString());
		builder.registerTypeAdapter(Path.class, toString);
		Gson gson = builder.create();
		Configuration config = gson.fromJson(reader, Configuration.class);
		Retriever retriever = Configuration.build(config);
		String[] cols = args[1].split(":");
		ModuleRevisionId rootId = Retriever.makeRevision(cols[0], cols[1], cols[2]);
		MarunOutputReport reports = retriever.collect(rootId, args[0]);
		Setup self = new Setup(reports.getDependencies());
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("resolve", reports);
		values.put("mains", self.findMain());
		values.put("resources", self.extractor.getEmbededResources("(marun)?"));
		values.put("failures", self.extractor.getFailureClasses());
		values.put("duplicates", self.extractor.getDuplicateEntries());
		gson.toJson(values, System.out);
	}
}
