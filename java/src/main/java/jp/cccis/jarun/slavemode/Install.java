package jp.cccis.jarun.slavemode;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;

import javax.script.ScriptException;

import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.retrieve.RetrieveReport;

import jp.cccis.jarun.Retriever;
import jp.cccis.jarun.configure.Configuration;

public class Install {
	public static void main(final String... args)
			throws ParseException, IOException, NoSuchMethodException, ScriptException {
		Configuration config = JsonParser.parse(System.in, Configuration.class);
		Retriever retriever = Configuration.build(config);
		String[] cols = args[2].split(":");
		ResolveReport resolved = retriever.resolve(cols[0], cols[1], cols[2], args[1]);
		RetrieveReport reported = retriever.retrieve(Paths.get(args[0]), resolved);
	}
}
