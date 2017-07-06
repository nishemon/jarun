package jp.cccis.jarun.slavemode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;

import jp.cccis.jarun.Retriever;
import jp.cccis.jarun.configure.Configuration;

public class Resolve {
	public static void main(final String... args) throws ParseException, IOException {
		Configuration config = JsonParser.parse(System.in, Configuration.class);
		Retriever retriever = Configuration.build(config);
		Path dir = Paths.get("./lib");
		retriever.fetch(dir, args[0], args[1], args[2], args[3]);
	}
}
