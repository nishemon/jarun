package jp.cccis.marun.configure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.ivy.plugins.resolver.BintrayResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

import jp.cccis.marun.repository.ivy.S3DownloadOnlyRepository;
import lombok.Data;

@Data
public class Repository {
	private String baseurl;
	private String type = "maven";
	private String name;
	private String accessKey;
	private String secretKey;

	public static RepositoryResolver build(final Repository conf) throws IllegalConfigurationException {
		switch (conf.name) {
		case "jcenter":
		case "bintray":
			if (conf.baseurl == null) {
				conf.baseurl = "https://jcenter.bintray.com/";
			}
			break;
		case "central":
			if (conf.baseurl == null) {
				conf.baseurl = IBiblioResolver.DEFAULT_M2_ROOT;
			}
		}
		URI root;
		try {
			root = new URI(conf.baseurl);
		} catch (URISyntaxException e) {
			throw new IllegalConfigurationException("Invalid baseurl '%s'", conf.baseurl);
		}
		final String host = root.getHost();
		final String scheme = root.getScheme();
		if (host == null || scheme == null) {
			throw new IllegalConfigurationException("Invalid baseurl '%s'", conf.baseurl);
		}
		if (host.contentEquals("jcenter.bintray.com")) {
			return new BintrayResolver();
		}
		RepositoryResolver ret = null;
		switch (conf.type.toLowerCase(Locale.ENGLISH)) {
		case "maven":
			IBiblioResolver resolver = new IBiblioResolver();
			resolver.setM2compatible(true);
			resolver.setName(root.getHost());
			resolver.setRoot(root.toString());
			ret = resolver;
			break;
		}
		if (ret != null) {
			if (scheme.contentEquals("s3")) {
				ret.setRepository(new S3DownloadOnlyRepository(host, conf.accessKey, conf.secretKey));
			}
			return ret;
		}
		throw new IllegalConfigurationException("Invalid type '%s'", conf.type);
	}
}
