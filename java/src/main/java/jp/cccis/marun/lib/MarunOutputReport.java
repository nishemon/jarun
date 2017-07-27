package jp.cccis.marun.lib;

import java.nio.file.Path;
import java.util.List;

import lombok.Getter;

@Getter
public class MarunOutputReport {
	public static enum RetrieveStatus {
		NOT_FOUND, CANT_DOWNLOAD
	}

	@Getter
	public static class JarStatus {
		Path path;
		String source;
		String id;
		String revision;
		RetrieveStatus status;
	}

	List<JarStatus> dependencies;
}
