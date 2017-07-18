package jp.cccis.marun.lib;

import java.io.File;
import java.util.List;

import lombok.Getter;

@Getter
public class MarunOutputReport {
	public static enum RetrieveStatus {
		NOT_FOUND, CANT_DOWNLOAD
	}

	public static class JarStatus {
		File file;
		String source;
		String id;
		String revision;
		RetrieveStatus status;
	}

	List<JarStatus> dependency;
}
