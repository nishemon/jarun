package jp.cccis.marun.lib;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import lombok.Value;

public final class ResourceFinder {
	@Value
	public static class Entry {
		private Path filepath;
		private String packageName;
		private String resourceName;
	}

	public static List<Entry> findResourcesInDir(final String packageName, final Path dir) throws IOException {
		List<Entry> resources = new ArrayList<>();

		DirectoryStream<Path> ds = Files.newDirectoryStream(dir);
		for (Path p : (Iterable<Path>) () -> ds.iterator()) {
			if (Files.isDirectory(p)) {
				resources.addAll(findResourcesInDir(packageName + "." + p.getFileName(), p));
			} else {
				resources.add(new Entry(p, packageName, p.getFileName().toString()));
			}
		}
		return resources;
	}

	public static List<Entry> findResourcesInJarFile(final Path jarFilePath) throws IOException {
		List<Entry> resources = new ArrayList<>();
		try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
			jarFile.getManifest().getEntries().get("Main-Class");
			jarFile.stream().forEach(j -> {
				if (j.getName().startsWith("META-INF/")) {
					return;
				}
				if (!j.isDirectory()) {
					Path p = Paths.get(j.getName()).normalize();
					Path parent = p.getParent();
					String pname = parent != null ? parent.toString().replace('/', '.') : "";
					resources.add(new Entry(jarFilePath, pname, p.getFileName().toString()));
				}
			});
		}
		return resources;
	}
}