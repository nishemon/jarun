package jp.cccis.marun.repository.ivy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.namespace.NameSpaceHelper;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.parser.m2.DefaultPomDependencyMgt;
import org.apache.ivy.plugins.parser.m2.PomDependencyMgt;
import org.apache.ivy.plugins.parser.m2.PomReader;
import org.apache.ivy.plugins.parser.m2.PomReader.PomDependencyData;
import org.apache.ivy.plugins.parser.m2.PomReader.PomDependencyMgtElement;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.url.URLResource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.xml.sax.SAXException;

import jp.cccis.marun.repository.ivy.PomModuleDescriptorBuilder.PomDependencyDescriptor;

/**
 * A parser for Maven 2 POM.
 * <p>
 * The configurations used in the generated module descriptor mimics the behavior defined by maven 2
 * scopes, as documented here:<br/>
 * http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html The
 * PomModuleDescriptorParser use a PomDomReader to read the pom, and the PomModuleDescriptorBuilder
 * to write the ivy module descriptor using the info read by the PomDomReader.
 */
public final class PomCustomModuleDescriptorParser implements ModuleDescriptorParser {

	private static final PomCustomModuleDescriptorParser INSTANCE = new PomCustomModuleDescriptorParser();

	public static PomCustomModuleDescriptorParser getInstance() {
		return INSTANCE;
	}

	private PomCustomModuleDescriptorParser() {
	}

	@Override
	public void toIvyFile(final InputStream is, final Resource res, final File destFile, final ModuleDescriptor md)
			throws ParseException, IOException {
		try {
			XmlModuleDescriptorWriter.write(md, destFile);
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	@Override
	public boolean accept(final Resource res) {
		return res.getName().endsWith(".pom") || res.getName().endsWith("pom.xml")
				|| res.getName().endsWith("project.xml");
	}

	@Override
	public String toString() {
		return "pom parser";
	}

	@Override
	public Artifact getMetadataArtifact(final ModuleRevisionId mrid, final Resource res) {
		return DefaultArtifact.newPomArtifact(mrid, new Date(res.getLastModified()));
	}

	@Override
	public String getType() {
		return "pom";
	}

	@Override
	public ModuleDescriptor parseDescriptor(final ParserSettings ivySettings, final URL descriptorURL,
			final boolean validate) throws ParseException, IOException {
		final URLResource resource = new URLResource(descriptorURL);
		return parseDescriptor(ivySettings, descriptorURL, resource, validate);
	}

	@Override
	public ModuleDescriptor parseDescriptor(final ParserSettings ivySettings, final URL descriptorURL,
			final Resource res, final boolean validate) throws ParseException, IOException {

		final PomModuleDescriptorBuilder mdBuilder;

		try {
			mdBuilder = buildDescriptor(ivySettings, descriptorURL, res);
		} catch (final SAXException e) {
			throw newParserException(e);
		}

		return mdBuilder.getModuleDescriptor();
	}

	private PomModuleDescriptorBuilder buildDescriptor(final ParserSettings ivySettings, final URL descriptorURL,
			final Resource res) throws IOException, SAXException, ParseException {
		final PomModuleDescriptorBuilder mdBuilder = new PomModuleDescriptorBuilder(this, res, ivySettings);

		final PomReader domReader = new PomReader(descriptorURL, res);
		domReader.setProperty("parent.version", domReader.getParentVersion());
		domReader.setProperty("parent.groupId", domReader.getParentGroupId());
		domReader.setProperty("project.parent.version", domReader.getParentVersion());
		domReader.setProperty("project.parent.groupId", domReader.getParentGroupId());

		final Map<String, String> pomProperties = domReader.getPomProperties();
		for (final Map.Entry<String, String> prop : pomProperties.entrySet()) {
			domReader.setProperty(prop.getKey(), prop.getValue());
			mdBuilder.addProperty(prop.getKey(), prop.getValue());
		}

		final ModuleDescriptor parentDescr;
		if (domReader.hasParent()) {
			// Is there any other parent properties?

			final ModuleRevisionId parentModRevID = ModuleRevisionId.newInstance(
					domReader.getParentGroupId(), domReader.getParentArtifactId(),
					domReader.getParentVersion());
			final ResolvedModuleRevision parentModule = parseOtherPom(ivySettings, parentModRevID);
			if (parentModule == null) {
				throw new IOException("Impossible to load parent for " + res.getName() + "."
						+ " Parent=" + parentModRevID);
			}
			parentDescr = parentModule.getDescriptor();
			if (parentDescr != null) {
				final Map<String, String> parentPomProps = PomModuleDescriptorBuilder
						.extractPomProperties(parentDescr.getExtraInfos());
				for (final Map.Entry<String, String> prop : parentPomProps.entrySet()) {
					domReader.setProperty(prop.getKey(), prop.getValue());
				}
			}
		} else {
			parentDescr = null;
		}

		final String groupId = domReader.getGroupId();
		final String artifactId = domReader.getArtifactId();
		final String version = domReader.getVersion();
		mdBuilder.setModuleRevId(groupId, artifactId, version);

		mdBuilder.setHomePage(domReader.getHomePage());
		mdBuilder.setDescription(domReader.getDescription());
		mdBuilder.setLicenses(domReader.getLicenses());

		final ModuleRevisionId relocation = domReader.getRelocation();

		if (relocation != null) {
			if (groupId != null && artifactId != null
					&& artifactId.equals(relocation.getName())
					&& groupId.equals(relocation.getOrganisation())) {
				Message.error("Relocation to an other version number not supported in ivy : "
						+ mdBuilder.getModuleDescriptor().getModuleRevisionId()
						+ " relocated to " + relocation
						+ ". Please update your dependency to directly use the right version.");
				Message.warn("Resolution will only pick dependencies of the relocated element."
						+ "  Artefact and other metadata will be ignored.");
				final ResolvedModuleRevision relocatedModule = parseOtherPom(ivySettings, relocation);
				if (relocatedModule == null) {
					throw new ParseException("impossible to load module " + relocation
							+ " to which "
							+ mdBuilder.getModuleDescriptor().getModuleRevisionId()
							+ " has been relocated", 0);
				}
				final DependencyDescriptor[] dds = relocatedModule.getDescriptor().getDependencies();
				for (final DependencyDescriptor dd : dds) {
					mdBuilder.addDependency(dd);
				}
			} else {
				Message.info(mdBuilder.getModuleDescriptor().getModuleRevisionId()
						+ " is relocated to " + relocation
						+ ". Please update your dependencies.");
				Message.verbose("Relocated module will be considered as a dependency");
				final DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(
						mdBuilder.getModuleDescriptor(), relocation, true, false, true);
				/* Map all public dependencies */
				final Configuration[] m2Confs = PomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS;
				for (final Configuration conf : m2Confs) {
					if (Visibility.PUBLIC.equals(conf.getVisibility())) {
						dd.addDependencyConfiguration(conf.getName(), conf.getName());
					}
				}
				mdBuilder.addDependency(dd);
			}
		} else {
			domReader.setProperty("project.groupId", groupId);
			domReader.setProperty("pom.groupId", groupId);
			domReader.setProperty("groupId", groupId);
			domReader.setProperty("project.artifactId", artifactId);
			domReader.setProperty("pom.artifactId", artifactId);
			domReader.setProperty("artifactId", artifactId);
			domReader.setProperty("project.version", version);
			domReader.setProperty("pom.version", version);
			domReader.setProperty("version", version);

			if (parentDescr != null) {
				mdBuilder.addExtraInfos(parentDescr.getExtraInfos());

				// add dependency management info from parent
				final List<PomDependencyMgt> depMgt = PomModuleDescriptorBuilder
						.getDependencyManagements(parentDescr);
				for (PomDependencyMgt dep : depMgt) {
					if (dep instanceof PomDependencyMgtElement) {
						dep = domReader.new PomDependencyMgtElement((PomDependencyMgtElement) dep);
					}
					mdBuilder.addDependencyMgt(dep);
				}

				// add plugins from parent
				final List<PomDependencyMgt> plugins = PomModuleDescriptorBuilder.getPlugins(parentDescr);
				for (final PomDependencyMgt dep : plugins) {
					mdBuilder.addPlugin(dep);
				}
			}

			for (final PomDependencyMgt dep : (List<PomDependencyMgt>) domReader.getDependencyMgt()) {
				if ("import".equals(dep.getScope())) {
					final ModuleRevisionId importModRevID = ModuleRevisionId.newInstance(
							dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
					final ResolvedModuleRevision importModule = parseOtherPom(ivySettings, importModRevID);
					if (importModule == null) {
						throw new IOException("Impossible to import module for "
								+ res.getName() + "." + " Import=" + importModRevID);
					}
					final ModuleDescriptor importDescr = importModule.getDescriptor();

					// add dependency management info from imported module
					final List<PomDependencyMgt> depMgt = PomModuleDescriptorBuilder
							.getDependencyManagements(importDescr);
					for (final PomDependencyMgt importedDepMgt : depMgt) {
						mdBuilder.addDependencyMgt(new DefaultPomDependencyMgt(
								importedDepMgt.getGroupId(),
								importedDepMgt.getArtifactId(),
								importedDepMgt.getVersion(), importedDepMgt.getScope(),
								importedDepMgt.getExcludedModules()));
					}

				} else {
					mdBuilder.addDependencyMgt(dep);
				}
			}

			for (final Iterator it = domReader.getDependencies().iterator(); it.hasNext();) {
				final PomReader.PomDependencyData dep = (PomReader.PomDependencyData) it.next();
				mdBuilder.addDependency(res, dep);
			}

			if (parentDescr != null) {
				for (final DependencyDescriptor descriptor : parentDescr.getDependencies()) {
					if (descriptor instanceof PomDependencyDescriptor) {
						final PomDependencyData parentDep = ((PomDependencyDescriptor) descriptor)
								.getPomDependencyData();
						final PomDependencyData dep = domReader.new PomDependencyData(parentDep);
						mdBuilder.addDependency(res, dep);
					} else {
						mdBuilder.addDependency(descriptor);
					}
				}
			}

			for (final Iterator it = domReader.getPlugins().iterator(); it.hasNext();) {
				final PomReader.PomPluginElement plugin = (PomReader.PomPluginElement) it.next();
				mdBuilder.addPlugin(plugin);
			}

			mdBuilder.addMainArtifact(artifactId, domReader.getPackaging());
			mdBuilder.addArtifact(artifactId);

			addSourcesAndJavadocArtifactsIfPresent(mdBuilder, ivySettings);
		}
		return mdBuilder;
	}

	private static void addSourcesAndJavadocArtifactsIfPresent(final PomModuleDescriptorBuilder mdBuilder,
			final ParserSettings ivySettings) {
		if (mdBuilder.getMainArtifact() == null) {
			// no main artifact in pom, we don't need to search for meta artifacts
			return;
		}
		final ModuleDescriptor md = mdBuilder.getModuleDescriptor();
		final ModuleRevisionId mrid = md.getModuleRevisionId();
		final DependencyResolver resolver = ivySettings.getResolver(mrid);

		if (resolver == null) {
			Message.debug("no resolver found for " + mrid
					+ ": no source or javadoc artifact lookup");
			return;
		}
		final ArtifactOrigin mainArtifact = resolver.locate(mdBuilder.getMainArtifact());

		if (ArtifactOrigin.isUnknown(mainArtifact)) {
			return;
		}
		final String mainArtifactLocation = mainArtifact.getLocation();

		final ArtifactOrigin sourceArtifact = resolver.locate(mdBuilder.getSourceArtifact());
		if (!ArtifactOrigin.isUnknown(sourceArtifact)
				&& !sourceArtifact.getLocation().equals(mainArtifactLocation)) {
			Message.debug("source artifact found for " + mrid);
			mdBuilder.addSourceArtifact();
		} else {
			// it seems that sometimes the 'src' classifier is used instead of 'sources'
			// Cfr. IVY-1138
			final ArtifactOrigin srcArtifact = resolver.locate(mdBuilder.getSrcArtifact());
			if (!ArtifactOrigin.isUnknown(srcArtifact)
					&& !srcArtifact.getLocation().equals(mainArtifactLocation)) {
				Message.debug("source artifact found for " + mrid);
				mdBuilder.addSrcArtifact();
			} else {
				Message.debug("no source artifact found for " + mrid);
			}
		}
		final ArtifactOrigin javadocArtifact = resolver.locate(mdBuilder.getJavadocArtifact());
		if (!ArtifactOrigin.isUnknown(javadocArtifact)
				&& !javadocArtifact.getLocation().equals(mainArtifactLocation)) {
			Message.debug("javadoc artifact found for " + mrid);
			mdBuilder.addJavadocArtifact();
		} else {
			Message.debug("no javadoc artifact found for " + mrid);
		}
	}

	private static ResolvedModuleRevision parseOtherPom(final ParserSettings ivySettings,
			final ModuleRevisionId parentModRevID) throws ParseException {
		DependencyDescriptor dd = new DefaultDependencyDescriptor(parentModRevID, true);
		ResolveData data = IvyContext.getContext().getResolveData();
		if (data == null) {
			final ResolveEngine engine = IvyContext.getContext().getIvy().getResolveEngine();
			final ResolveOptions options = new ResolveOptions();
			options.setDownload(false);
			data = new ResolveData(engine, options);
		}

		final DependencyResolver resolver = ivySettings.getResolver(parentModRevID);
		if (resolver == null) {
			// TODO: Throw exception here?
			return null;
		}
		dd = NameSpaceHelper.toSystem(dd, ivySettings.getContextNamespace());
		return resolver.getDependency(dd, data);
	}

	private static ParseException newParserException(final Exception e) {
		Message.error(e.getMessage());
		final ParseException pe = new ParseException(e.getMessage(), 0);
		pe.initCause(e);
		return pe;
	}

}
