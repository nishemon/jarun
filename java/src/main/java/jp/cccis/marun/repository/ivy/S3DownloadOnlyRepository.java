package jp.cccis.marun.repository.ivy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.util.FileUtil;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class S3DownloadOnlyRepository extends AbstractRepository {
	private static final long LIFETIME = TimeUnit.SECONDS.toMillis(30);
	private long lastSync = 0;
	private S3URI lastRoot = null;
	private NavigableMap<String, S3ObjectSummary> objCache = null;
	AmazonS3 s3;

	public S3DownloadOnlyRepository(final String bucket, final String accessKey, final String secretKey) {
		AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
		AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
		AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard().withCredentials(provider);
		builder.setRegion("us-west-1");
		AmazonS3 bootS3 = builder.build();
		Region targetRegion = RegionUtils.getRegion(bootS3.getBucketLocation(bucket));
		if (!"us-west-1".contentEquals(targetRegion.getName())) {
			builder.setRegion(targetRegion.getName());
			this.s3 = builder.build();
		} else {
			this.s3 = bootS3;
		}
	}

	@Override
	public S3Resource getResource(final String source) {
		S3URI base = new S3URI(source);
		updateSummraizeCache(base);
		S3ObjectSummary summary = this.objCache.get(base.getKey());
		return new S3Resource(summary, source) {
			@Override
			public Resource clone(final String cloneName) {
				return getResource(cloneName);
			}

			@Override
			public InputStream openStream() throws IOException {
				return S3DownloadOnlyRepository.this.s3.getObject(base.getBucket(), base.getKey()).getObjectContent();
			}
		};
	}

	@Override
	public void get(final String source, final File destination) throws IOException {
		S3Resource resource = getResource(source);
		try {
			fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
			RepositoryCopyProgressListener progressListener = new RepositoryCopyProgressListener(this);
			progressListener.setTotalLength(resource.getContentLength());
			FileUtil.copy(resource.openStream(), new FileOutputStream(destination), progressListener);
		} catch (IOException e) {
			fireTransferError(e);
			throw e;
		} catch (RuntimeException e) {
			fireTransferError(e);
			throw e;
		}
		fireTransferCompleted(resource.getContentLength());
	}

	private void updateSummraizeCache(final S3URI target) {
		long now = System.currentTimeMillis();
		if (this.lastRoot == null || this.lastSync + LIFETIME < now || !this.lastRoot.isParentOf(target)) {
			S3URI dir = target.getCurrentDirectory();
			ListObjectsV2Result v2result = this.s3.listObjectsV2(dir.getBucket(), dir.getKey());
			assert v2result.getNextContinuationToken() == null;
			List<S3ObjectSummary> summaries = v2result.getObjectSummaries();
			this.objCache = new TreeMap<>();
			this.lastRoot = dir;
			this.lastSync = now;
			summaries.stream().forEach(s -> this.objCache.put(s.getKey(), s));
		}
	}

	@Override
	public List<String> list(final String parent) throws IOException {
		S3URI base = new S3URI(parent);
		updateSummraizeCache(base);
		String bucket = base.getBucket();
		String key = base.getKey();
		List<String> entries = new ArrayList<>();
		for (S3ObjectSummary s : this.objCache.tailMap(key, true).values()) {
			String name = s.getKey();
			if (!name.startsWith(key)) {
				break;
			}
			if (name.indexOf('/', name.length() + 1) == -1) {
				try {
					entries.add(new URI("s3", bucket, name, null).toString());
				} catch (URISyntaxException e) {
					throw new IOException(e);
				}
			}
		}
		return entries;
	}
}

@Getter
class S3URI {
	private final String bucket;
	private final String key;

	S3URI(final String url) {
		URI parser = URI.create(url).normalize();
		this.bucket = parser.getHost();
		String path = parser.getPath();
		this.key = path.replaceAll("/+", "/").replaceAll("^/", "");
	}

	private S3URI(final String bucket, final String key) {
		this.bucket = bucket;
		this.key = key;
	}

	S3URI getCurrentDirectory() {
		if (this.key.isEmpty() || this.key.charAt(this.key.length() - 1) == '/') {
			return this;
		}
		return new S3URI(this.bucket, this.key.replaceAll("/[^/]+$", ""));
	}

	boolean isParentOf(final S3URI childish) {
		return this.bucket.contentEquals(childish.bucket) && childish.key.startsWith(this.key);
	}
}

@AllArgsConstructor
abstract class S3Resource implements Resource {
	private final S3ObjectSummary summary;
	@Getter
	private final String name;

	@Override
	public long getLastModified() {
		if (this.summary == null) {
			return 0;
		}
		Date last = this.summary.getLastModified();
		if (last == null) {
			return 0;
		}
		return last.getTime();
	}

	@Override
	public long getContentLength() {
		return this.summary == null ? 0 : this.summary.getSize();
	}

	@Override
	public boolean exists() {
		return this.summary != null;
	}

	@Override
	public boolean isLocal() {
		return false;
	}
}
