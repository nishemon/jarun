package jp.cccis.marun.repository;

import java.util.List;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;

import lombok.Getter;
import lombok.Setter;

@Getter
public class MarunRetrieveReport {
	private final List<ArtifactDownloadReport> downloadedList;
	@Setter
	private List<ArtifactDownloadReport> failedList;
	@Setter
	private List<ModuleRevisionId> unresolvedList;

	public MarunRetrieveReport(final List<ArtifactDownloadReport> reports) {
		this.downloadedList = reports;
	}
}
