package jp.cccis.jarun;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.List;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.IBiblioResolver;

public class Resolver {
	private final Ivy ivy;

	public Resolver(final Path cacheDir) {
		IvySettings settings = new IvySettings();
		IBiblioResolver resolver = new IBiblioResolver();
		resolver.setM2compatible(true);
		resolver.setName("ibiblio");
		resolver.setRoot("https://jcenter.bintray.com");
		settings.addResolver(resolver);
		settings.setDefaultResolver("ibiblio");
		DefaultRepositoryCacheManager cm = new DefaultRepositoryCacheManager();
		cm.setBasedir(cacheDir.toFile());
		settings.addRepositoryCacheManager(cm);
		this.ivy = Ivy.newInstance(settings);
	}

	public void resolve(final String organisation, final String name, final String revision, final String scope)
			throws ParseException, IOException {
		ModuleId modId = new ModuleId(organisation, name);
		ModuleRevisionId id = new ModuleRevisionId(modId, revision);
		ResolveOptions resolveOptions = new ResolveOptions().setConfs(new String[] { scope });
		ResolveReport resolveReport = this.ivy.resolve(id, resolveOptions, false);

		for (ArtifactDownloadReport report : resolveReport.getAllArtifactsReports()) {
			System.out.println(report);
		}
		RetrieveOptions retriveOptions = new RetrieveOptions().setConfs(new String[] { scope });
		this.ivy.retrieve(resolveReport.getModuleDescriptor().getResolvedModuleRevisionId(),
				"lib/[artifact]-[revision].[ext]", retriveOptions);
		for (ArtifactDownloadReport report : resolveReport.getAllArtifactsReports()) {
			System.out.println(report);
		}

		if (resolveReport.hasError()) {
			List<String> problems = resolveReport.getAllProblemMessages();
			if (problems != null && !problems.isEmpty()) {
				StringBuffer errorMsgs = new StringBuffer();
				for (String problem : problems) {
					errorMsgs.append(problem);
					errorMsgs.append("\n");
				}
				System.err.println("Errors encountered during dependency resolution for package :");
				System.err.println(errorMsgs);
			}
		} else {
			System.out.println("Dependencies in file were successfully resolved");
		}
	}

	public static void main(final String[] args) throws ParseException, IOException {
		Resolver self = new Resolver(Paths.get("cache"));
		self.resolve("org.apache.mahout", "mahout-core", "+", "runtime");
	}
}
