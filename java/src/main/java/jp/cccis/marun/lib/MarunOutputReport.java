package jp.cccis.marun.lib;

import java.nio.file.Path;
import java.util.List;

import lombok.Getter;
import lombok.ToString;

@Getter
public class MarunOutputReport {
	public static enum RetrieveStatus {
		NOT_FOUND, CANT_DOWNLOAD
	}

	@ToString
	@Getter
	public static class JarStatus {
		Path path;
		String source;
		String id;
		String revision;
		String classifier;
		RetrieveStatus status;
	}

	List<JarStatus> dependencies;
}
