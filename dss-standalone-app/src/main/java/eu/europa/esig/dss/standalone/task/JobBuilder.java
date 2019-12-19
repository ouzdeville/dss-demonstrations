package eu.europa.esig.dss.standalone.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.client.http.DSSFileLoader;
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.tsl.cache.CacheCleaner;
import eu.europa.esig.dss.tsl.function.OfficialJournalSchemeInformationURI;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.sync.AcceptAllStrategy;

public class JobBuilder {

	private static final Logger LOG = LoggerFactory.getLogger(JobBuilder.class);
	
	private Properties prop;
	private final String propertiesFilePath = "src/main/resources/app.config";

	private TrustedListsCertificateSource tslCertificateSource;
	private File cacheDirectory;

	public JobBuilder() {
		tslCertificateSource = new TrustedListsCertificateSource();
		getProperties();
		cacheDirectory = tlCacheDirectory();
	}
	
	private void getProperties() {
		prop = new Properties();
		try (InputStream is = new FileInputStream(propertiesFilePath)) {
		    prop.load(is);
		} catch (IOException e) {
			LOG.warn("Could not load properties file : {}", e.getMessage(), e);
		}
	}
	
	public TLValidationJob job() {	    
		TLValidationJob job = new TLValidationJob();
		job.setOnlineDataLoader(onlineLoader());
		job.setOfflineDataLoader(offlineLoader());
		job.setTrustedListCertificateSource(tslCertificateSource);
		job.setSynchronizationStrategy(new AcceptAllStrategy());
		job.setCacheCleaner(cacheCleaner());

		LOTLSource europeanLOTL = europeanLOTL();
		job.setListOfTrustedListSources(europeanLOTL);

		return job;
	}

	private DSSFileLoader onlineLoader() {
		FileCacheDataLoader offlineFileLoader = new FileCacheDataLoader();
		offlineFileLoader.setCacheExpirationTime(0);
		offlineFileLoader.setDataLoader(dataLoader());
		offlineFileLoader.setFileCacheDirectory(cacheDirectory);
		return offlineFileLoader;
	}

	private DSSFileLoader offlineLoader() {
		FileCacheDataLoader offlineFileLoader = new FileCacheDataLoader();
		offlineFileLoader.setCacheExpirationTime(Long.MAX_VALUE);
		offlineFileLoader.setDataLoader(new IgnoreDataLoader()); // do not download from Internet
		offlineFileLoader.setFileCacheDirectory(cacheDirectory);
		return offlineFileLoader;
	}

	private CommonsDataLoader dataLoader() {
		return new CommonsDataLoader();
	}

	private LOTLSource europeanLOTL() {
		LOTLSource lotlSource = new LOTLSource();
		lotlSource.setUrl(prop.getProperty("lotl.url"));
		lotlSource.setCertificateSource(officialJournalContentKeyStore());
		lotlSource.setSigningCertificatesAnnouncementPredicate(new OfficialJournalSchemeInformationURI(prop.getProperty("oj.url")));
		lotlSource.setPivotSupport(true);
		return lotlSource;
	}

	private CacheCleaner cacheCleaner() {
		CacheCleaner cacheCleaner = new CacheCleaner();
		cacheCleaner.setCleanMemory(true);
		cacheCleaner.setCleanFileSystem(true);
		cacheCleaner.setDSSFileLoader(getDSSFileLoader());
		return cacheCleaner;
	}

	private CertificateSource officialJournalContentKeyStore() {
		try {
			return new KeyStoreCertificateSource(new File(prop.getProperty("keystore.path")), prop.getProperty("keystore.type"), prop.getProperty("keystore.password"));
		} catch (IOException e) {
			throw new DSSException("Unable to load the keystore", e);
		}
	}

	private FileCacheDataLoader getDSSFileLoader() {
		FileCacheDataLoader fileLoader = new FileCacheDataLoader();
		fileLoader.setCacheExpirationTime(0);
		fileLoader.setFileCacheDirectory(cacheDirectory);
		return fileLoader;
	}

	private File tlCacheDirectory() {
		File rootFolder = new File(System.getProperty("java.io.tmpdir"));
		File tslCache = new File(rootFolder, "dss-tsl-loader");
		LOG.info("TL Cache folder : {}", tslCache.getAbsolutePath());
		return tslCache;
	}

	public TrustedListsCertificateSource getCertificateSources() {
		return tslCertificateSource;
	}

}
