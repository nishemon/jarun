package jp.cccis.marun.configure;

import java.io.File;
import java.util.List;

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.plugins.resolver.ChainResolver;

import jp.cccis.marun.repository.Retriever;
import lombok.Data;

@Data
public class Configuration {
	private boolean fast;
	private String workdir;
	private List<Repository> repositories;

	public static Retriever build(final Configuration conf) throws IllegalConfigurationException {
		DefaultRepositoryCacheManager cm = new DefaultRepositoryCacheManager();
		File rootDir = new File(conf.workdir);
		File cacheDir = new File(rootDir, "ivy");
		cacheDir.mkdir();
		cm.setBasedir(cacheDir);
		ChainResolver resolver = new ChainResolver();
		resolver.setReturnFirst(conf.fast);
		conf.repositories.stream().map(Repository::build).forEach(resolver::add);

		return new Retriever(cm, resolver);
	}
}
