package jp.cccis.marun.repository.ivy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.util.FileUtil;

public class S3DownloadOnlyRepository extends AbstractRepository {

	@Override
	public S3Resource getResource(final String source) throws IOException {
		// TODO 自動生成されたメソッド・スタブ
		return new S3Resource() {

			@Override
			public Resource clone(final String cloneName) {
				// TODO 自動生成されたメソッド・スタブ
				return null;
			}

			@Override
			public InputStream openStream() throws IOException {
				// TODO 自動生成されたメソッド・スタブ
				return null;
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

	@Override
	public List<String> list(final String parent) throws IOException {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}

}

abstract class S3Resource implements Resource {
	private S3ObjectSummary summary;

	@Override
	public String getName() {
		return this.summary.getKey();
	}

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
