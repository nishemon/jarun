package jp.cccis.marun.cli;

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

import jp.cccis.marun.configure.Configuration;
import jp.cccis.marun.pod.PodAnalyzer;
import jp.cccis.marun.repository.MarunOutputReport;
import jp.cccis.marun.repository.Retriever;
import jp.cccis.marun.repository.MarunOutputReport.JarStatus;

public class Setup {
	private final PodAnalyzer extractor;

	private Setup(final List<JarStatus> jars) throws ClassNotFoundException, IOException {
		List<Path> paths = new ArrayList<>();
		for (JarStatus js : jars) {
			Path p = js.getPath();
			if (p != null) {
				paths.add(p);
			} else {
				System.err.println(js);
			}
		}
		this.extractor = new PodAnalyzer(paths);
	}

	private List<String> findMain() {
		List<Method> methods = this.extractor.findStaticMethod("main", String[].class);
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
		List<ModuleRevisionId> requires = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String[] cols = args[1].split(":", 4);
			if (3 < cols.length) {
				requires.add(Retriever.makeRevision(cols[0], cols[1], cols[2], cols[3]));
			} else {
				requires.add(Retriever.makeRevision(cols[0], cols[1], cols[2]));
			}
		}
		MarunOutputReport reports = retriever.collect(requires, args[0]);
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
