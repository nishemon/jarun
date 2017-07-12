package jp.cccis.jarun;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.BintrayResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

public class Retriever {
	private final Ivy ivy;

	public Retriever(final RepositoryCacheManager cacheManager, final DependencyResolver resolver) {
		IvySettings settings = new IvySettings();
		settings.addResolver(resolver);
		settings.setDefaultResolver(resolver.getName());
		settings.addRepositoryCacheManager(cacheManager);
		settings.setDefaultRepositoryCacheManager(cacheManager);
		this.ivy = Ivy.newInstance(settings);
	}

	public static ModuleRevisionId makeRevision(final String organisation, final String name, final String revision) {
		ModuleId modId = new ModuleId(organisation, name);
		return new ModuleRevisionId(modId, revision);
	}

	public ResolveReport resolve(final ModuleRevisionId id, final String scope, final boolean resolveOnly)
			throws ParseException, IOException {
		ResolveOptions resolveOptions = new ResolveOptions().setConfs(new String[] { scope });
		resolveOptions.setDownload(!resolveOnly);
		return this.ivy.resolve(id, resolveOptions, true);
	}

	private static Path linkOrCopy(final Path dest, final Path existing) throws IOException {
		Files.createDirectories(dest.getParent());

		IOException ex;
		try {
			Files.createLink(dest, existing);
			return dest;
		} catch (IOException e) {
			ex = e;
		}
		try {
			Files.createSymbolicLink(dest, existing);
			return dest;
		} catch (IOException e) {
			e.addSuppressed(ex);
			ex = e;
		}
		try {
			Files.copy(existing, dest);
		} catch (IOException e) {
			e.addSuppressed(ex);
			throw e;
		}
		return dest;
	}

	public static List<ArtifactDownloadReport> retrieve(final ResolveReport resolveReport,
			final ModuleRevisionId originalId) {
		List<ModuleRevisionId> idList = new ArrayList<>();
		ModuleDescriptor md = resolveReport.getModuleDescriptor();
		List<ArtifactDownloadReport> reports = new ArrayList<>();
		for (IvyNode node : (List<IvyNode>) resolveReport.getDependencies()) {
			List<ArtifactDownloadReport> results = downloadNode(node);
			if (results.isEmpty()) {
				idList.add(node.getId());
			} else {
				for (ArtifactDownloadReport r : results) {
					if (r.getDownloadStatus() != DownloadStatus.FAILED) {
						reports.add(r);
					}
				}
			}
		}
		return reports;
	}

	// private static ArtifactDownloadReport[] downloadRoot(final List<IvyNode> nodes, final
	// ModuleRevisionId rootId)
	// throws IOException {
	// Optional<IvyNode> rootNode = nodes.stream().filter(n -> n.getId() == rootId).findFirst();
	// return rootNode.map(Retriever::downloadNode).orElseThrow(IOException::new);
	// }

	private static List<ArtifactDownloadReport> downloadNode(final IvyNode in) {
		ResolvedModuleRevision resolved = in.getModuleRevision();
		if (resolved == null) {
			return Collections.emptyList();
		}
		DependencyResolver resolver = in.getModuleRevision().getResolver();
		Artifact[] master = in.getDescriptor().getArtifacts("master");
		DownloadReport downloaded = resolver.download(master, new DownloadOptions());
		return Arrays.asList(downloaded.getArtifactsReports());
	}

	private static RepositoryResolver buildResolver(final URI root) {
		if (root.getHost().contentEquals("jcenter.bintray.com")) {
			return new BintrayResolver();
		}
		IBiblioResolver resolver = new IBiblioResolver();
		resolver.setM2compatible(true);
		resolver.setName(root.getHost());
		resolver.setRoot(root.toString());
		return resolver;
	}

	public static void main(final String... args) throws ParseException, IOException {
		DefaultRepositoryCacheManager cm = new DefaultRepositoryCacheManager();
		new File("./ivy").mkdirs();
		cm.setBasedir(new File("./ivy"));
		List<RepositoryResolver> resolverList = Arrays.stream(args, 0, args.length - 1).map(URI::create)
				.map(Retriever::buildResolver).collect(Collectors.toList());
		ChainResolver resolver = new ChainResolver();
		resolver.setReturnFirst(true);
		resolverList.forEach(resolver::add);

		Retriever retriever = new Retriever(cm, resolver);
		String art = args[args.length - 1];
		String[] v = art.split(":", 3);
		ModuleRevisionId rootId = Retriever.makeRevision(v[0], v[1], v[2]);
		ResolveReport report = retriever.resolve(rootId, "runtime", false);
		Retriever.retrieve(report, rootId);
	}
}
