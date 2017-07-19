package jp.cccis.marun.lib;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.resolver.BintrayResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.util.Message;

import jp.cccis.marun.lib.MarunOutputReport.JarStatus;
import jp.cccis.marun.lib.MarunOutputReport.RetrieveStatus;

public class Retriever {
	private final Ivy ivy;

	public Retriever(final RepositoryCacheManager cacheManager, final DependencyResolver resolver) {
		ModuleDescriptorParserRegistry.getInstance().addParser(PomCustomModuleDescriptorParser.getInstance());
		IvySettings settings = new IvySettings();
		settings.addResolver(resolver);
		settings.setDefaultResolver(resolver.getName());
		settings.addRepositoryCacheManager(cacheManager);
		settings.setDefaultRepositoryCacheManager(cacheManager);
		Message.setDefaultLogger(new SyserrMessageLogger(Message.MSG_INFO));
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

	public static MarunRetrieveReport retrieve(final ResolveReport resolveReport) {
		List<ModuleRevisionId> unresolved = new ArrayList<>();
		List<ArtifactDownloadReport> reports = new ArrayList<>();
		List<ArtifactDownloadReport> fails = new ArrayList<>();
		for (IvyNode node : (List<IvyNode>) resolveReport.getDependencies()) {
			if (node.isCompletelyEvicted()) {
				continue;
			}
			List<ArtifactDownloadReport> results = downloadNode(node);
			if (results.isEmpty()) {
				unresolved.add(node.getId());
			} else {
				// TODO: compare node.getId() and node.getResolvedId() for update
				for (ArtifactDownloadReport r : results) {
					if (r.getDownloadStatus() != DownloadStatus.FAILED) {
						reports.add(r);
					} else {
						fails.add(r);
					}
				}
			}
		}
		MarunRetrieveReport rep = new MarunRetrieveReport(reports);
		rep.setFailedList(fails);
		rep.setUnresolvedList(unresolved);
		return rep;
	}

	private static String toGradleIdFormatString(final ModuleId id) {
		return String.format("%s:%s", id.getOrganisation(), id.getName());
	}

	public MarunOutputReport collect(final ModuleRevisionId rootId, final String scope)
			throws ParseException, IOException {
		ResolveReport report = resolve(rootId, scope, false);
		MarunRetrieveReport rep = Retriever.retrieve(report);
		MarunOutputReport output = new MarunOutputReport();
		List<JarStatus> statuses = new ArrayList<>();
		for (ArtifactDownloadReport r : rep.getDownloadedList()) {
			JarStatus js = new JarStatus();
			js.file = r.getLocalFile();
			ModuleRevisionId revId = r.getArtifact().getModuleRevisionId();
			js.id = toGradleIdFormatString(revId.getModuleId());
			js.revision = revId.getRevision();
			js.source = r.getArtifactOrigin().getLocation();
			statuses.add(js);
		}
		for (ArtifactDownloadReport f : rep.getFailedList()) {
			JarStatus js = new JarStatus();
			ModuleRevisionId revId = f.getArtifact().getModuleRevisionId();
			js.id = toGradleIdFormatString(revId.getModuleId());
			js.revision = revId.getRevision();
			js.status = RetrieveStatus.CANT_DOWNLOAD;
			statuses.add(js);
		}
		for (ModuleRevisionId id : rep.getUnresolvedList()) {
			JarStatus js = new JarStatus();
			js.id = toGradleIdFormatString(id.getModuleId());
			js.revision = id.getRevision();
			js.status = RetrieveStatus.NOT_FOUND;
			statuses.add(js);
		}
		output.dependency = statuses;
		return output;
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
		DependencyResolver resolver = resolved.getResolver();
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
		retriever.collect(rootId, "runtime");
	}
}
