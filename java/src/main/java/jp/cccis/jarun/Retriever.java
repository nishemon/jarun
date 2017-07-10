package jp.cccis.jarun;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveEngine;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.retrieve.RetrieveReport;
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
		this.ivy = Ivy.newInstance(settings);
	}

	public ResolveReport resolve(final String organisation, final String name, final String revision,
			final String scope)
			throws ParseException, IOException {
		ModuleId modId = new ModuleId(organisation, name);
		ModuleRevisionId id = new ModuleRevisionId(modId, revision);
		ResolveOptions resolveOptions = new ResolveOptions().setConfs(new String[] { scope });
		resolveOptions.setDownload(true);
		return this.ivy.resolve(id, resolveOptions, true);
	}

	private static Path linkOrCopy(final Path dest, final Path existing) throws IOException {
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

	public RetrieveReport retrieve(final Path dir, final ResolveReport resolveReport) throws IOException {
		ModuleDescriptor md = resolveReport.getModuleDescriptor();
		ArtifactDownloadReport[] rootReports = downloadRoot(resolveReport.getDependencies(), md.getModuleRevisionId());
		RetrieveOptions retrieveOptions = new RetrieveOptions().setConfs(resolveReport.getConfigurations());
		retrieveOptions.setDestArtifactPattern(dir + "/[artifact]-[revision].[ext]");
		RetrieveEngine engine = this.ivy.getRetrieveEngine();
		RetrieveReport reports = engine.retrieve(md.getResolvedModuleRevisionId(), retrieveOptions);
		Path root = reports.getRetrieveRoot().toPath();
		for (ArtifactDownloadReport rr : rootReports) {
			File base = rr.getLocalFile();
			Path dest = linkOrCopy(root.resolve(base.getName()), base.toPath());
			reports.addCopiedFile(dest.toFile(), rr);
		}
		return reports;
	}

	private static ArtifactDownloadReport[] downloadRoot(final List<IvyNode> nodes, final ModuleRevisionId rootId)
			throws IOException {
		Optional<IvyNode> rootNode = nodes.stream().filter(n -> n.getId() == rootId).findFirst();
		return rootNode.map(Retriever::downloadNode).orElseThrow(IOException::new);
	}

	private static ArtifactDownloadReport[] downloadNode(final IvyNode in) {
		DependencyResolver resolver = in.getModuleRevision().getResolver();
		Artifact[] master = in.getDescriptor().getArtifacts("master");
		DownloadReport downloaded = resolver.download(master, new DownloadOptions());
		return downloaded.getArtifactsReports();
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
		cm.setBasedir(new File("/var/cache/jarun/ivy"));
		List<RepositoryResolver> resolverList = Arrays.stream(args, 0, args.length - 1).map(URI::create)
				.map(Retriever::buildResolver).collect(Collectors.toList());
		ChainResolver resolver = new ChainResolver();
		resolver.setReturnFirst(true);
		resolverList.forEach(resolver::add);

		Retriever retriever = new Retriever(cm, resolver);
		String art = args[args.length - 1];
		String[] v = art.split(":", 3);
		retriever.fetch(Paths.get("./lib"), v[0], v[1], v[2], "runtime");
	}
}
