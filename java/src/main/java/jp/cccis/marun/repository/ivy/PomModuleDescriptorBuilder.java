package jp.cccis.marun.repository.ivy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExtraInfoHolder;
import org.apache.ivy.core.module.descriptor.License;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.OverrideDependencyDescriptorMediator;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.parser.m2.DefaultPomDependencyMgt;
import org.apache.ivy.plugins.parser.m2.PomDependencyMgt;
import org.apache.ivy.plugins.parser.m2.PomReader.PomDependencyData;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;

import lombok.Value;

/**
 * Build a module descriptor. This class handle the complexity of the structure of an ivy
 * ModuleDescriptor and isolate the PomModuleDescriptorParser from it.
 */
public class PomModuleDescriptorBuilder {

	private static final int DEPENDENCY_MANAGEMENT_KEY_PARTS_COUNT = 4;

	protected static final Configuration[] MAVEN2_CONFIGURATIONS = new Configuration[] {
			new Configuration("default", Visibility.PUBLIC,
					"runtime dependencies and master artifact can be used with this conf",
					new String[] { "runtime", "master" }, true, null),
			new Configuration("master", Visibility.PUBLIC,
					"contains only the artifact published by this module itself, "
							+ "with no transitive dependencies",
					new String[0], true, null),
			new Configuration("compile", Visibility.PUBLIC,
					"this is the default scope, used if none is specified. "
							+ "Compile dependencies are available in all classpaths.",
					new String[0], true, null),
			new Configuration(
					"provided",
					Visibility.PUBLIC,
					"this is much like compile, but indicates you expect the JDK or a container "
							+ "to provide it. "
							+ "It is only available on the compilation classpath, and is not transitive.",
					new String[0], true, null),
			new Configuration("runtime", Visibility.PUBLIC,
					"this scope indicates that the dependency is not required for compilation, "
							+ "but is for execution. It is in the runtime and test classpaths, "
							+ "but not the compile classpath.",
					new String[] { "compile" }, true,
					null),
			new Configuration(
					"test",
					Visibility.PRIVATE,
					"this scope indicates that the dependency is not required for normal use of "
							+ "the application, and is only available for the test compilation and "
							+ "execution phases.",
					new String[] { "runtime" }, true, null),
			new Configuration(
					"system",
					Visibility.PUBLIC,
					"this scope is similar to provided except that you have to provide the JAR "
							+ "which contains it explicitly. The artifact is always available and is not "
							+ "looked up in a repository.",
					new String[0], true, null),
			new Configuration("sources", Visibility.PUBLIC,
					"this configuration contains the source artifact of this module, if any.",
					new String[0], true, null),
			new Configuration("javadoc", Visibility.PUBLIC,
					"this configuration contains the javadoc artifact of this module, if any.",
					new String[0], true, null),
			new Configuration("optional", Visibility.PUBLIC, "contains all optional dependencies",
					new String[0], true, null) };

	static final Map<String, ConfMapper> MAVEN2_CONF_MAPPING = new HashMap<>();

	private static final String DEPENDENCY_MANAGEMENT = "m:dependency.management";

	private static final String PROPERTIES = "m:properties";

	private static final String EXTRA_INFO_DELIMITER = "__";

	private static final Collection<String> JAR_PACKAGINGS = Arrays.asList(new String[] {
			"ejb", "bundle", "maven-plugin", "eclipse-plugin", "jbi-component",
			"jbi-shared-library", "orbit", "hk2-jar" });

	interface ConfMapper {
		void addMappingConfs(DefaultDependencyDescriptor dd, boolean isOptional);
	}

	static {
		MAVEN2_CONF_MAPPING.put("compile", (dd, isOptional) -> {
			if (isOptional) {
				dd.addDependencyConfiguration("optional", "compile(*)");
				// dd.addDependencyConfiguration("optional", "provided(*)");
				dd.addDependencyConfiguration("optional", "master(*)");

			} else {
				dd.addDependencyConfiguration("compile", "compile(*)");
				// dd.addDependencyConfiguration("compile", "provided(*)");
				dd.addDependencyConfiguration("compile", "master(*)");
				dd.addDependencyConfiguration("runtime", "runtime(*)");
			}
		});
		MAVEN2_CONF_MAPPING.put("provided", (dd, isOptional) -> {
			if (isOptional) {
				dd.addDependencyConfiguration("optional", "compile(*)");
				dd.addDependencyConfiguration("optional", "provided(*)");
				dd.addDependencyConfiguration("optional", "runtime(*)");
				dd.addDependencyConfiguration("optional", "master(*)");
			} else {
				dd.addDependencyConfiguration("provided", "compile(*)");
				dd.addDependencyConfiguration("provided", "provided(*)");
				dd.addDependencyConfiguration("provided", "runtime(*)");
				dd.addDependencyConfiguration("provided", "master(*)");
			}
		});
		MAVEN2_CONF_MAPPING.put("runtime", (dd, isOptional) -> {
			if (isOptional) {
				dd.addDependencyConfiguration("optional", "compile(*)");
				dd.addDependencyConfiguration("optional", "provided(*)");
				dd.addDependencyConfiguration("optional", "master(*)");

			} else {
				dd.addDependencyConfiguration("runtime", "compile(*)");
				dd.addDependencyConfiguration("runtime", "runtime(*)");
				dd.addDependencyConfiguration("runtime", "master(*)");
			}
		});
		MAVEN2_CONF_MAPPING.put("test", (dd, isOptional) -> {
			// optional doesn't make sense in the test scope
			dd.addDependencyConfiguration("test", "runtime(*)");
			dd.addDependencyConfiguration("test", "master(*)");
		});
		MAVEN2_CONF_MAPPING.put("system", (dd, isOptional) -> dd.addDependencyConfiguration("system", "master(*)"));
	}

	private final PomModuleDescriptor ivyModuleDescriptor;

	private ModuleRevisionId mrid;

	private DefaultArtifact mainArtifact;

	private final ParserSettings parserSettings;

	private static final String WRONG_NUMBER_OF_PARTS_MSG = "what seemed to be a dependency "
			+ "management extra info exclusion had the wrong number of parts (should have 2) ";

	public PomModuleDescriptorBuilder(final ModuleDescriptorParser parser, final Resource res,
			final ParserSettings ivySettings) {
		this.ivyModuleDescriptor = new PomModuleDescriptor(parser, res);
		this.ivyModuleDescriptor.setResolvedPublicationDate(new Date(res.getLastModified()));
		for (final Configuration element : MAVEN2_CONFIGURATIONS) {
			this.ivyModuleDescriptor.addConfiguration(element);
		}
		this.ivyModuleDescriptor.setMappingOverride(true);
		this.ivyModuleDescriptor.addExtraAttributeNamespace("m", Ivy.getIvyHomeURL() + "maven");
		this.parserSettings = ivySettings;
	}

	public ModuleDescriptor getModuleDescriptor() {
		return this.ivyModuleDescriptor;
	}

	public void setModuleRevId(final String groupId, final String artifactId, final String version) {
		this.mrid = ModuleRevisionId.newInstance(groupId, artifactId, version);
		this.ivyModuleDescriptor.setModuleRevisionId(this.mrid);

		if ((version == null) || version.endsWith("SNAPSHOT")) {
			this.ivyModuleDescriptor.setStatus("integration");
		} else {
			this.ivyModuleDescriptor.setStatus("release");
		}
	}

	public void setHomePage(final String homePage) {
		this.ivyModuleDescriptor.setHomePage(homePage);
	}

	public void setDescription(final String description) {
		this.ivyModuleDescriptor.setDescription(description);
	}

	public void setLicenses(final License[] licenses) {
		for (final License license : licenses) {
			this.ivyModuleDescriptor.addLicense(license);
		}
	}

	public void addMainArtifact(final String artifactId, final String packaging) {
		String ext;

		/*
		 * TODO: we should make packaging to ext mapping configurable, since it's not possible to
		 * cover all cases.
		 */
		if ("pom".equals(packaging)) {
			// no artifact defined! Add the default artifact if it exist.
			final DependencyResolver resolver = this.parserSettings.getResolver(this.mrid);

			if (resolver != null) {
				final DefaultArtifact artifact = new DefaultArtifact(this.mrid, new Date(), artifactId, "jar",
						"jar");
				final ArtifactOrigin artifactOrigin = resolver.locate(artifact);

				if (!ArtifactOrigin.isUnknown(artifactOrigin)) {
					this.mainArtifact = artifact;
					this.ivyModuleDescriptor.addArtifact("master", this.mainArtifact);
				}
			}

			return;
		} else if (JAR_PACKAGINGS.contains(packaging)) {
			ext = "jar";
		} else if ("pear".equals(packaging)) {
			ext = "phar";
		} else {
			ext = packaging;
		}

		this.mainArtifact = new DefaultArtifact(this.mrid, new Date(), artifactId, packaging, ext);
		this.ivyModuleDescriptor.addArtifact("master", this.mainArtifact);
	}

	private final Map<ScopedModule, DefaultDependencyDescriptor> ddCache = new HashMap<>();

	public void addDependency(final Resource res, final PomDependencyData dep) {
		String scope = dep.getScope();
		if ((scope != null) && (scope.length() > 0) && !MAVEN2_CONF_MAPPING.containsKey(scope)) {
			// unknown scope, defaulting to 'compile'
			scope = "compile";
		}

		String version = dep.getVersion();
		version = (version == null || version.length() == 0) ? getDefaultVersion(dep) : version;
		final ModuleRevisionId moduleRevId = ModuleRevisionId.newInstance(dep.getGroupId(),
				dep.getArtifactId(), version);

		// Some POMs depend on theirselfves, don't add this dependency: Ivy doesn't allow this!
		// Example: https://repo1.maven.org/maven2/net/jini/jsk-platform/2.1/jsk-platform-2.1.pom
		final ModuleRevisionId mRevId = this.ivyModuleDescriptor.getModuleRevisionId();
		if ((mRevId != null) && mRevId.getModuleId().equals(moduleRevId.getModuleId())) {
			return;
		}

		scope = (scope == null || scope.length() == 0) ? getDefaultScope(dep) : scope;

		final String optionalizedScope = dep.isOptional() ? "optional" : scope;
		final DefaultDependencyDescriptor dd = getArtifactDependencyDescriptor(dep, optionalizedScope, scope,
				moduleRevId);
		if ((dep.getClassifier() != null)
				|| ((dep.getType() != null) && !"jar".equals(dep.getType()))) {
			final Map<String, String> extraAtt = new HashMap<>();
			String type = "jar";
			if (dep.getType() != null) {
				type = dep.getType();
			}
			String ext = type;

			// if type is 'test-jar', the extension is 'jar' and the classifier is 'tests'
			// Cfr. http://maven.apache.org/guides/mini/guide-attached-tests.html
			if ("test-jar".equals(type)) {
				ext = "jar";
				extraAtt.put("m:classifier", "tests");
			} else if (JAR_PACKAGINGS.contains(type)) {
				ext = "jar";
			}

			// we deal with classifiers by setting an extra attribute and forcing the
			// dependency to assume such an artifact is published
			if (dep.getClassifier() != null) {
				extraAtt.put("m:classifier", dep.getClassifier());
			}
			final DefaultDependencyArtifactDescriptor depArtifact = new DefaultDependencyArtifactDescriptor(
					dd, dd.getDependencyId().getName(), type, ext, null, extraAtt);
			// here we have to assume a type and ext for the artifact, so this is a limitation
			// compared to how m2 behave with classifiers
			dd.addDependencyArtifact(optionalizedScope, depArtifact);
		}

		// experimentation shows the following, excluded modules are
		// inherited from parent POMs if either of the following is true:
		// the <exclusions> element is missing or the <exclusions> element
		// is present, but empty.
		List<ModuleId> excluded = dep.getExcludedModules();
		if (excluded.isEmpty()) {
			excluded = getDependencyMgtExclusions(this.ivyModuleDescriptor, dep.getGroupId(), dep.getArtifactId());
		}
		for (final ModuleId excludedModule : excluded) {
			final String[] confs = dd.getModuleConfigurations();
			for (final String conf : confs) {
				dd.addExcludeRule(conf, new DefaultExcludeRule(new ArtifactId(excludedModule,
						PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION,
						PatternMatcher.ANY_EXPRESSION), ExactPatternMatcher.INSTANCE, null));
			}
		}
	}

	@Value
	private static class ScopedModule {
		private ModuleRevisionId moduleRevId;
		private String optionalizedScope;
	}

	private DefaultDependencyDescriptor getArtifactDependencyDescriptor(final PomDependencyData dep,
			final String optionalizedScope, final String scope,
			final ModuleRevisionId moduleRevId) {
		final ScopedModule key = new ScopedModule(moduleRevId, optionalizedScope);
		DefaultDependencyDescriptor dd = this.ddCache.get(key);
		if (dd == null) {
			dd = new PomDependencyDescriptor(dep, this.ivyModuleDescriptor, moduleRevId);
			this.ddCache.put(key, dd);

			final ConfMapper mapping = MAVEN2_CONF_MAPPING.get(scope);
			mapping.addMappingConfs(dd, dep.isOptional());
			this.ivyModuleDescriptor.addDependency(dd);
		}
		return dd;
	}

	public void addDependency(final DependencyDescriptor descriptor) {
		// Some POMs depend on themselves through their parent pom, don't add this dependency
		// since Ivy doesn't allow this!
		// Example:
		// https://repo1.maven.org/maven2/com/atomikos/atomikos-util/3.6.4/atomikos-util-3.6.4.pom
		final ModuleId dependencyId = descriptor.getDependencyId();
		final ModuleRevisionId mRevId = this.ivyModuleDescriptor.getModuleRevisionId();
		if ((mRevId != null) && mRevId.getModuleId().equals(dependencyId)) {
			return;
		}

		this.ivyModuleDescriptor.addDependency(descriptor);
	}

	public void addDependencyMgt(final PomDependencyMgt dep) {
		this.ivyModuleDescriptor.addDependencyManagement(dep);

		final String key = getDependencyMgtExtraInfoKeyForVersion(dep.getGroupId(), dep.getArtifactId());
		overwriteExtraInfoIfExists(key, dep.getVersion());
		if (dep.getScope() != null) {
			final String scopeKey = getDependencyMgtExtraInfoKeyForScope(dep.getGroupId(),
					dep.getArtifactId());
			overwriteExtraInfoIfExists(scopeKey, dep.getScope());
		}
		if (!dep.getExcludedModules().isEmpty()) {
			final String exclusionPrefix = getDependencyMgtExtraInfoPrefixForExclusion(
					dep.getGroupId(), dep.getArtifactId());
			for (ListIterator<ModuleId> iter = ((List<ModuleId>) dep.getExcludedModules()).listIterator(); iter
					.hasNext();) {
				final ModuleId excludedModule = iter.next();
				overwriteExtraInfoIfExists(
						exclusionPrefix + iter.previousIndex(),
						excludedModule.getOrganisation() + EXTRA_INFO_DELIMITER
								+ excludedModule.getName());
			}
		}
		// dependency management info is also used for version mediation of transitive dependencies
		this.ivyModuleDescriptor.addDependencyDescriptorMediator(
				ModuleId.newInstance(dep.getGroupId(), dep.getArtifactId()),
				ExactPatternMatcher.INSTANCE,
				new OverrideDependencyDescriptorMediator(null, dep.getVersion()));
	}

	public void addPlugin(final PomDependencyMgt plugin) {
		final String pluginValue = plugin.getGroupId() + EXTRA_INFO_DELIMITER + plugin.getArtifactId()
				+ EXTRA_INFO_DELIMITER + plugin.getVersion();
		ExtraInfoHolder extraInfoByTagName = this.ivyModuleDescriptor
				.getExtraInfoByTagName("m:maven.plugins");
		if (extraInfoByTagName == null) {
			extraInfoByTagName = new ExtraInfoHolder();
			extraInfoByTagName.setName("m:maven.plugins");
			this.ivyModuleDescriptor.addExtraInfo(extraInfoByTagName);
		}
		String pluginExtraInfo = extraInfoByTagName.getContent();
		if (pluginExtraInfo == null) {
			pluginExtraInfo = pluginValue;
		} else {
			pluginExtraInfo = pluginExtraInfo + "|" + pluginValue;
		}
		extraInfoByTagName.setContent(pluginExtraInfo);
	}

	public static List<PomDependencyMgt> getPlugins(final ModuleDescriptor md) {
		final String plugins = md.getExtraInfoContentByTagName("m:maven.plugins");
		if (plugins == null) {
			return Collections.emptyList();
		}
		final List<PomDependencyMgt> result = new ArrayList<>();
		final String[] pluginsArray = plugins.split("\\|");
		for (final String element : pluginsArray) {
			final String[] parts = element.split(EXTRA_INFO_DELIMITER);
			result.add(new PomPluginElement(parts[0], parts[1], parts[2]));
		}

		return result;
	}

	private static class PomPluginElement implements PomDependencyMgt {
		private final String groupId;

		private final String artifactId;

		private final String version;

		public PomPluginElement(final String groupId, final String artifactId, final String version) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
		}

		@Override
		public String getGroupId() {
			return this.groupId;
		}

		@Override
		public String getArtifactId() {
			return this.artifactId;
		}

		@Override
		public String getVersion() {
			return this.version;
		}

		@Override
		public String getScope() {
			return null;
		}

		@Override
		public List /* <ModuleId> */ getExcludedModules() {
			return Collections.EMPTY_LIST; // probably not used?
		}
	}

	private String getDefaultVersion(final PomDependencyData dep) {
		final ModuleId moduleId = ModuleId.newInstance(dep.getGroupId(), dep.getArtifactId());
		if (this.ivyModuleDescriptor.getDependencyManagementMap().containsKey(moduleId)) {
			return this.ivyModuleDescriptor.getDependencyManagementMap().get(
					moduleId).getVersion();
		}
		final String key = getDependencyMgtExtraInfoKeyForVersion(dep.getGroupId(), dep.getArtifactId());
		return this.ivyModuleDescriptor.getExtraInfoContentByTagName(key);
	}

	private String getDefaultScope(final PomDependencyData dep) {
		String result;
		final ModuleId moduleId = ModuleId.newInstance(dep.getGroupId(), dep.getArtifactId());
		if (this.ivyModuleDescriptor.getDependencyManagementMap().containsKey(moduleId)) {
			result = this.ivyModuleDescriptor.getDependencyManagementMap().get(
					moduleId).getScope();
		} else {
			final String key = getDependencyMgtExtraInfoKeyForScope(dep.getGroupId(), dep.getArtifactId());
			result = this.ivyModuleDescriptor.getExtraInfoContentByTagName(key);
		}
		if ((result == null) || !MAVEN2_CONF_MAPPING.containsKey(result)) {
			result = "compile";
		}
		return result;
	}

	private static String getDependencyMgtExtraInfoKeyForVersion(final String groupId, final String artifaceId) {
		return DEPENDENCY_MANAGEMENT + EXTRA_INFO_DELIMITER + groupId + EXTRA_INFO_DELIMITER
				+ artifaceId + EXTRA_INFO_DELIMITER + "version";
	}

	private static String getDependencyMgtExtraInfoKeyForScope(final String groupId, final String artifaceId) {
		return DEPENDENCY_MANAGEMENT + EXTRA_INFO_DELIMITER + groupId + EXTRA_INFO_DELIMITER
				+ artifaceId + EXTRA_INFO_DELIMITER + "scope";
	}

	private static String getPropertyExtraInfoKey(final String propertyName) {
		return PROPERTIES + EXTRA_INFO_DELIMITER + propertyName;
	}

	private static String getDependencyMgtExtraInfoPrefixForExclusion(final String groupId,
			final String artifaceId) {
		return DEPENDENCY_MANAGEMENT + EXTRA_INFO_DELIMITER + groupId + EXTRA_INFO_DELIMITER
				+ artifaceId + EXTRA_INFO_DELIMITER + "exclusion_";
	}

	private static List<ModuleId> getDependencyMgtExclusions(final ModuleDescriptor descriptor,
			final String groupId, final String artifactId) {
		if (descriptor instanceof PomModuleDescriptor) {
			final PomDependencyMgt dependencyMgt = ((PomModuleDescriptor) descriptor)
					.getDependencyManagementMap().get(ModuleId.newInstance(groupId, artifactId));
			if (dependencyMgt != null) {
				return dependencyMgt.getExcludedModules();
			}
		}
		final String exclusionPrefix = getDependencyMgtExtraInfoPrefixForExclusion(groupId, artifactId);
		final List<ModuleId> exclusionIds = new LinkedList<>();
		for (final ExtraInfoHolder extraInfoHolder : descriptor.getExtraInfos()) {
			final String key = extraInfoHolder.getName();
			if (!key.startsWith(exclusionPrefix)) {
				continue;
			}
			final String fullExclusion = extraInfoHolder.getContent();
			final String[] exclusionParts = fullExclusion.split(EXTRA_INFO_DELIMITER);
			if (exclusionParts.length != 2) {
				Message.error(WRONG_NUMBER_OF_PARTS_MSG + exclusionParts.length + " : "
						+ fullExclusion);
				continue;
			}
			exclusionIds.add(ModuleId.newInstance(exclusionParts[0], exclusionParts[1]));
		}
		return exclusionIds;
	}

	public static Map<ModuleId, String> getDependencyManagementMap(final ModuleDescriptor md) {
		final Map<ModuleId, String> ret = new LinkedHashMap<>();
		if (md instanceof PomModuleDescriptor) {
			for (final Map.Entry<ModuleId, PomDependencyMgt> e : ((PomModuleDescriptor) md).getDependencyManagementMap()
					.entrySet()) {
				final PomDependencyMgt dependencyMgt = e.getValue();
				ret.put(e.getKey(), dependencyMgt.getVersion());
			}
		} else {
			for (final ExtraInfoHolder extraInfoHolder : md.getExtraInfos()) {
				final String key = extraInfoHolder.getName();
				if (!key.startsWith(DEPENDENCY_MANAGEMENT)) {
					continue;
				}
				final String[] parts = key.split(EXTRA_INFO_DELIMITER);
				if (parts.length != DEPENDENCY_MANAGEMENT_KEY_PARTS_COUNT) {
					Message.warn("what seem to be a dependency management extra info "
							+ "doesn't match expected pattern: " + key);
				} else {
					ret.put(ModuleId.newInstance(parts[1], parts[2]),
							extraInfoHolder.getContent());
				}
			}
		}
		return ret;
	}

	public static List<PomDependencyMgt> getDependencyManagements(final ModuleDescriptor md) {
		final List<PomDependencyMgt> result = new ArrayList<>();

		if (md instanceof PomModuleDescriptor) {
			result.addAll(((PomModuleDescriptor) md).getDependencyManagementMap().values());
		} else {
			for (final ExtraInfoHolder extraInfoHolder : md.getExtraInfos()) {
				final String key = extraInfoHolder.getName();
				if (!key.startsWith(DEPENDENCY_MANAGEMENT)) {
					continue;
				}
				final String[] parts = key.split(EXTRA_INFO_DELIMITER);
				if (parts.length != DEPENDENCY_MANAGEMENT_KEY_PARTS_COUNT) {
					Message.warn("what seem to be a dependency management extra info "
							+ "doesn't match expected pattern: " + key);
				} else {
					final String versionKey = DEPENDENCY_MANAGEMENT + EXTRA_INFO_DELIMITER + parts[1]
							+ EXTRA_INFO_DELIMITER + parts[2] + EXTRA_INFO_DELIMITER
							+ "version";
					final String scopeKey = DEPENDENCY_MANAGEMENT + EXTRA_INFO_DELIMITER + parts[1]
							+ EXTRA_INFO_DELIMITER + parts[2] + EXTRA_INFO_DELIMITER + "scope";

					final String version = md.getExtraInfoContentByTagName(versionKey);
					final String scope = md.getExtraInfoContentByTagName(scopeKey);

					final List<ModuleId> exclusions = getDependencyMgtExclusions(md, parts[1], parts[2]);
					result.add(new DefaultPomDependencyMgt(parts[1], parts[2], version, scope,
							exclusions));
				}
			}
		}
		return result;
	}

	@Deprecated
	public void addExtraInfos(final Map<String, String> extraAttributes) {
		for (final Map.Entry<String, String> entry : extraAttributes.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			addExtraInfo(key, value);
		}
	}

	private void addExtraInfo(final String key, final String value) {
		if (this.ivyModuleDescriptor.getExtraInfoByTagName(key) == null) {
			this.ivyModuleDescriptor.getExtraInfos().add(new ExtraInfoHolder(key, value));
		}
	}

	private void overwriteExtraInfoIfExists(final String key, final String value) {
		boolean found = false;
		for (final ExtraInfoHolder extraInfoHolder : this.ivyModuleDescriptor.getExtraInfos()) {
			if (extraInfoHolder.getName().equals(key)) {
				extraInfoHolder.setContent(value);
				found = true;
			}
		}
		if (!found) {
			this.ivyModuleDescriptor.getExtraInfos().add(new ExtraInfoHolder(key, value));
		}
	}

	public void addExtraInfos(final List<ExtraInfoHolder> extraInfosHolder) {
		for (final ExtraInfoHolder extraInfoHolder : extraInfosHolder) {
			addExtraInfo(extraInfoHolder.getName(), extraInfoHolder.getContent());
		}
	}

	public static Map<String, String> extractPomProperties(final List<ExtraInfoHolder> extraInfos) {
		final Map<String, String> r = new HashMap<>();
		for (final ExtraInfoHolder extraInfoHolder : extraInfos) {
			if ((extraInfoHolder.getName()).startsWith(PROPERTIES)) {
				final String prop = (extraInfoHolder.getName()).substring(PROPERTIES.length()
						+ EXTRA_INFO_DELIMITER.length());
				r.put(prop, extraInfoHolder.getContent());
			}
		}
		return r;
	}

	public void addProperty(final String propertyName, final String value) {
		addExtraInfo(getPropertyExtraInfoKey(propertyName), value);
	}

	public Artifact getMainArtifact() {
		return this.mainArtifact;
	}

	public Artifact getSourceArtifact() {
		return new MDArtifact(this.ivyModuleDescriptor, this.mrid.getName(), "source", "jar", null,
				Collections.singletonMap("m:classifier", "sources"));
	}

	public Artifact getSrcArtifact() {
		return new MDArtifact(this.ivyModuleDescriptor, this.mrid.getName(), "source", "jar", null,
				Collections.singletonMap("m:classifier", "src"));
	}

	public Artifact getJavadocArtifact() {
		return new MDArtifact(this.ivyModuleDescriptor, this.mrid.getName(), "javadoc", "jar", null,
				Collections.singletonMap("m:classifier", "javadoc"));
	}

	public void addSourceArtifact() {
		this.ivyModuleDescriptor.addArtifact("sources", getSourceArtifact());
	}

	public void addSrcArtifact() {
		this.ivyModuleDescriptor.addArtifact("sources", getSrcArtifact());
	}

	public void addJavadocArtifact() {
		this.ivyModuleDescriptor.addArtifact("javadoc", getJavadocArtifact());
	}

	/**
	 * <code>DependencyDescriptor</code> that provides access to the original
	 * <code>PomDependencyData</code>.
	 */
	public static class PomDependencyDescriptor extends DefaultDependencyDescriptor {
		private final PomDependencyData pomDependencyData;

		PomDependencyDescriptor(final PomDependencyData pomDependencyData,
				final ModuleDescriptor moduleDescriptor, final ModuleRevisionId revisionId) {
			super(moduleDescriptor, revisionId, true, false, true);
			this.pomDependencyData = pomDependencyData;
		}

		/**
		 * Get PomDependencyData.
		 *
		 * @return PomDependencyData
		 */
		public PomDependencyData getPomDependencyData() {
			return this.pomDependencyData;
		}
	}

	public static class PomModuleDescriptor extends DefaultModuleDescriptor {
		private final Map<ModuleId, PomDependencyMgt> dependencyManagementMap = new HashMap<>();

		public PomModuleDescriptor(final ModuleDescriptorParser parser, final Resource res) {
			super(parser, res);
		}

		public void addDependencyManagement(final PomDependencyMgt dependencyMgt) {
			this.dependencyManagementMap.put(
					ModuleId.newInstance(dependencyMgt.getGroupId(), dependencyMgt.getArtifactId()),
					dependencyMgt);
		}

		public Map<ModuleId, PomDependencyMgt> getDependencyManagementMap() {
			return this.dependencyManagementMap;
		}
	}

	public void addArtifact(final String artifactId) {
	}
}
