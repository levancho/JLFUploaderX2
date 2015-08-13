package com.github.levancho.jsuplaoder.largefile.logic;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.github.levancho.jsuplaoder.largefile.exception.FileCorruptedException;
import com.github.levancho.jsuplaoder.largefile.exception.FileStillProcessingException;
import com.github.levancho.jsuplaoder.largefile.json.FileStateJson;
import com.github.levancho.jsuplaoder.largefile.json.FileStateJsonBase;
import com.github.levancho.jsuplaoder.largefile.json.InitializationConfiguration;
import com.github.levancho.jsuplaoder.largefile.json.PrepareUploadJson;
import com.github.levancho.jsuplaoder.largefile.limiter.RateLimiterConfigurationManager;
import com.github.levancho.jsuplaoder.largefile.limiter.RequestUploadProcessingConfiguration;
import com.github.levancho.jsuplaoder.largefile.utils.CRCHelper;
import com.github.levancho.jsuplaoder.largefile.utils.ProgressManager;
import com.github.levancho.jsuplaoder.largefile.utils.RemainingTimeEstimator;
import com.github.levancho.jsuplaoder.largefile.exception.InvalidCrcException;
import com.github.levancho.jsuplaoder.largefile.notifier.JLFUListenerPropagator;
import com.github.levancho.jsuplaoder.largefile.staticstate.JavaLargeFileUploaderService;
import com.github.levancho.jsuplaoder.largefile.staticstate.StaticStateIdentifierManager;
import com.github.levancho.jsuplaoder.largefile.staticstate.StaticStateManager;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.FileProgressStatus;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticFileState;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticStatePersistedOnFileSystemEntity;
import com.github.levancho.jsuplaoder.largefile.staticstate.StaticStateDirectoryManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import com.github.levancho.jsuplaoder.largefile.json.CRCResult;
import com.github.levancho.jsuplaoder.largefile.json.ProgressJson;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Maps.EntryTransformer;
import com.google.common.collect.Ordering;



@Component
@ManagedResource(objectName = "JavaLargeFileUploader:name=uploadServletProcessor")
public class UploadProcessor {

//	private static final Logger log = LoggerFactory.getLogger(UploadProcessor.class);

	private static Log log = LogFactory.getLog(UploadProcessor.class);

	@Autowired
	CRCHelper crcHelper;

	@Autowired
	RateLimiterConfigurationManager uploadProcessingConfigurationManager;

	@Autowired
	StaticStateManager<StaticStatePersistedOnFileSystemEntity> staticStateManager;

	@Autowired
	JavaLargeFileUploaderService<StaticStatePersistedOnFileSystemEntity> staticStateManagerService;

	@Autowired
	StaticStateIdentifierManager staticStateIdentifierManager;

	@Autowired
	StaticStateDirectoryManager staticStateDirectoryManager;

	@Autowired
	ProgressManager progressManager;
	
	@Autowired
	RemainingTimeEstimator remainingTimeEstimator;
	
	@Autowired
	private JLFUListenerPropagator uploadxListenerPropagator;

	public static final int SIZE_OF_FIRST_CHUNK_VALIDATION = 8192;

	/**
	 * Size of a slice <br>
	 * Default to 10MB.
	 */
	@Value("upx2{uploader.sliceSizeInBytes:10485760}")
	private long sliceSizeInBytes;

	/**
	 * Keeps the original name of the uploaded files.<br>
	 * If <code>false</code>, the name will be a {@link java.util.UUID} which will guarantee no name-collision.<br>
	 * Default to <code>false</code>
	 */
	@Value("upx2{uploader.keepOriginalFileName:false}")
	private boolean keepOriginalFileName;

	public InitializationConfiguration getConfig(UUID clientId) {

		// specify the client id
		if (clientId != null) {
			staticStateIdentifierManager.setIdentifier(clientId);
		}

		// prepare
		InitializationConfiguration config = new InitializationConfiguration();
		StaticStatePersistedOnFileSystemEntity entity = staticStateManager.getEntity();

		if (entity != null) {

			// order files by age
			Ordering<UUID> ordering = Ordering.from(new Comparator<StaticFileState>() {

				@Override
				public int compare(StaticFileState o1, StaticFileState o2) {
					int compareTo = o1.getStaticFileStateJson().getCreationDate().compareTo(o2.getStaticFileStateJson().getCreationDate());
					return compareTo != 0 ? compareTo : 1;
				}

			}).onResultOf(Functions.forMap(entity.getFileStates()));

			// apply comparator
			ImmutableSortedMap<UUID, StaticFileState> sortedMap = ImmutableSortedMap.copyOf(entity.getFileStates(), ordering);

			// fill pending files from static state
			final SortedMap<UUID, FileStateJson> transformEntries =
					Maps.transformEntries(sortedMap, new EntryTransformer<UUID, StaticFileState, FileStateJson>() {

						@Override
						public FileStateJson transformEntry(UUID fileId, StaticFileState value) {
							return getFileStateJson(fileId, value);
						}
					});

			// change keys
			Map<String, FileStateJson> newMap = Maps.newHashMap();
			for (Entry<UUID, FileStateJson> entry : transformEntries.entrySet()) {
				newMap.put(entry.getKey().toString(), entry.getValue());
				
				//also, if we have a configuration existing for this file, we resume it
				resume(entry.getKey());
			}

			// apply transformed map
			config.setPendingFiles(newMap);

		}

		// fill configuration
		config.setInByte(sliceSizeInBytes);

		return config;
	}




	private FileStateJson getFileStateJson(UUID fileId, StaticFileState value) {

		// process
		File file = new File(value.getAbsoluteFullPathOfUploadedFile());
		Long fileSize = file.length();

		FileStateJsonBase staticFileStateJson = value.getStaticFileStateJson();
		FileStateJson fileStateJson = new FileStateJson();
		fileStateJson.setFileComplete(staticFileStateJson.getCrcedBytes().equals(staticFileStateJson.getOriginalFileSizeInBytes()));
		fileStateJson.setFileCompletionInBytes(fileSize);
		fileStateJson.setOriginalFileName(staticFileStateJson.getOriginalFileName());
		fileStateJson.setOriginalFileSizeInBytes(staticFileStateJson.getOriginalFileSizeInBytes());
		fileStateJson.setRateInKiloBytes(staticFileStateJson.getRateInKiloBytes());
		fileStateJson.setCrcedBytes(staticFileStateJson.getCrcedBytes());
		fileStateJson.setFirstChunkCrc(staticFileStateJson.getFirstChunkCrc());
		fileStateJson.setCreationDate(staticFileStateJson.getCreationDate());
		log.debug("returning pending file " + fileStateJson.getOriginalFileName() + " with target size " +
				fileStateJson.getOriginalFileSizeInBytes() + " out of " + fileSize + " completed which includes " +
				fileStateJson.getCrcedBytes() + " bytes validated and " + (fileSize - fileStateJson.getCrcedBytes()) + " unvalidated.");

		return fileStateJson;
	}


	public UUID prepareUpload(Long size, String fileName, String crc)
			throws IOException {

		// retrieve model
		StaticStatePersistedOnFileSystemEntity model = staticStateManager.getEntity();

		// extract the extension of the filename
		String fileExtension = extractExtensionOfFileName(fileName);

		// create a new file for it
		UUID fileId = UUID.randomUUID();
		File file = new File(staticStateDirectoryManager.getUUIDFileParent(), keepOriginalFileName ? fileName : fileId + fileExtension);
		file.createNewFile();
		StaticFileState fileState = new StaticFileState();
		FileStateJsonBase jsonFileState = new FileStateJsonBase();
		fileState.setStaticFileStateJson(jsonFileState);
		fileState.setAbsoluteFullPathOfUploadedFile(file.getAbsolutePath());
		model.getFileStates().put(fileId, fileState);

		// add info to the state
		jsonFileState.setOriginalFileName(fileName);
		jsonFileState.setOriginalFileSizeInBytes(size);
		jsonFileState.setFirstChunkCrc(crc);
		jsonFileState.setCreationDate(new Date());

		// write the state
		staticStateManager.updateEntity(model);

		// call listener
		uploadxListenerPropagator.getPropagator().onFileUploadPrepared(staticStateIdentifierManager.getIdentifier(), fileId);

		// and returns the file identifier
		log.debug("File prepared for client " + staticStateIdentifierManager.getIdentifier() + " at path " + file.getAbsolutePath());
		return fileId;

	}


	public HashMap<String, UUID> prepareUpload(PrepareUploadJson[] fromJson)
			throws IOException {
		HashMap<String, UUID> returnMap = Maps.newHashMap();

		// for all of them
		for (PrepareUploadJson prepareUploadJson : fromJson) {

			// prepare it
			UUID idOfTheFile =
					prepareUpload(prepareUploadJson.getSize(), prepareUploadJson.getFileName(), prepareUploadJson.getCrc());

			// put in map
			returnMap.put(prepareUploadJson.getTempId().toString(), idOfTheFile);

			// notify listener
			uploadxListenerPropagator.getPropagator().onFileUploadPrepared(staticStateIdentifierManager.getIdentifier(), idOfTheFile);

		}

		// notify that all are processed
		uploadxListenerPropagator.getPropagator().onAllFileUploadsPrepared(staticStateIdentifierManager.getIdentifier(), returnMap.values());

		return returnMap;
	}


	private String extractExtensionOfFileName(String fileName) {
		String[] split = fileName.split("\\.");
		String fileExtension = "";
		if (split.length > 1) {
			if (split.length > 0) {
				fileExtension = '.' + split[split.length - 1];
			}
		}
		return fileExtension;
	}



	public void clearFile(UUID fileId)
			throws InterruptedException, ExecutionException, TimeoutException {

		// specify as paused
		pause(fileId);
		
		// delete
		staticStateManager.clearFile(fileId);

		// then call listener
		uploadxListenerPropagator.getPropagator().onFileUploadCancelled(staticStateIdentifierManager.getIdentifier(), fileId);
	}


	public void clearAll()
			throws InterruptedException, ExecutionException, TimeoutException {

		//amorce cancellation for all the files
		for (UUID fileId : staticStateManager.getEntity().getFileStates().keySet()) {
			pause(fileId);
		}
		
		// clear everything
		staticStateManager.clear();
	}




	public ProgressJson getProgress(UUID fileId)
			throws FileNotFoundException {
		
		// progress
		FileProgressStatus progress = progressManager.getProgress(fileId);
		
		//return values
		ProgressJson progressJson = new ProgressJson();
		if (progress != null) {
			progressJson.setProgress(progress.getProgress());
			progressJson.setEstimatedRemainingTimeInSeconds(progress.getEstimatedRemainingTimeInSeconds());
			progressJson.setUploadRate(progress.getUploadRate());
		} else {
			progressJson.setProgress(0f);
			progressJson.setEstimatedRemainingTimeInSeconds(0l);
			progressJson.setUploadRate(0l);
		}
		return progressJson;
	}
	

	public void setUploadRate(UUID fileId, Long rate) {

		// set the rate
		uploadProcessingConfigurationManager.assignRateToRequest(fileId, rate);

		// save it for the file with this file id
		StaticStatePersistedOnFileSystemEntity entity = staticStateManager.getEntity();
		entity.getFileStates().get(fileId).getStaticFileStateJson().setRateInKiloBytes(rate);

		// persist changes
		staticStateManager.updateEntity(entity);
	}


	public void pauseFile(List<UUID> uuids) {

		// for all these files
		for (UUID uuid : uuids) {

			//specifyStreamIsExpectedToClose
			pause(uuid);

			// then call listener
			uploadxListenerPropagator.getPropagator().onFileUploadPaused(staticStateIdentifierManager.getIdentifier(), uuid);
		}

	}


	public FileStateJson resumeFile(UUID fileId) throws FileNotFoundException {

		//check if we have this file
		StaticFileState value = staticStateManager.getEntity().getFileStates().get(fileId);
		if (value == null) {
			throw new FileNotFoundException("File with id " + fileId + " not found");
		}

		//resume the configuration
		resume(fileId);
		
		// then call listener
		uploadxListenerPropagator.getPropagator().onFileUploadResumed(staticStateIdentifierManager.getIdentifier(), fileId);

		// and return some information about it
		return getFileStateJson(fileId, value);
	}


	public void verifyCrcOfUncheckedPart(UUID fileId, String inputCrc)
			throws IOException, InvalidCrcException, FileCorruptedException, FileStillProcessingException {
		log.debug("validating the bytes that have not been validated from the previous interrupted upload for file " +
				fileId);

		// get entity
		StaticStatePersistedOnFileSystemEntity model = staticStateManager.getEntity();

		// get the file
		StaticFileState fileState = model.getFileStates().get(fileId);
		if (fileState == null) {
			throw new FileNotFoundException("File with id " + fileId + " not found");
		}
		File file = new File(fileState.getAbsoluteFullPathOfUploadedFile());


		// if the file does not exist, there is an issue!
		if (!file.exists()) {
			throw new FileNotFoundException("File with id " + fileId + " not found");
		}
		
		//get request conf
		RequestUploadProcessingConfiguration uploadProcessingConfiguration = uploadProcessingConfigurationManager.getUploadProcessingConfiguration(fileId);

		//unpause the file
		resume(fileId, uploadProcessingConfiguration);
		
		//check if this file is processing
		if (uploadProcessingConfiguration.isProcessing()) {
			throw new FileStillProcessingException(fileId);
		}

		// open the file stream
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(file);
			// skip the crced part
			fileInputStream.skip(fileState.getStaticFileStateJson().getCrcedBytes());
			
			// read the crc
			final CRCResult fileCrc = crcHelper.getBufferedCrc(fileInputStream);

			// compare them
			log.debug("validating chunk crc " + fileCrc.getCrcAsString() + " against " + inputCrc);
	
			// if not equal, we have an issue:
			if (!fileCrc.getCrcAsString().equals(inputCrc)) {
				log.debug("invalid crc ... now truncating file to match validated bytes " + fileState.getStaticFileStateJson().getCrcedBytes());
	
				// we are just sure now that the file before the crc validated is actually valid, and
				// after that, it seems it is not
				// so we get the file and remove everything after that crc validation so that user can
				// resume the fileupload from there.
	
				// truncate the file
				RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rwd");
				randomAccessFile.setLength(fileState.getStaticFileStateJson().getCrcedBytes());
				randomAccessFile.close();
	
				// throw the exception
				throw new InvalidCrcException(fileCrc.getCrcAsString(), inputCrc);
			}
			// if correct, we can add these bytes as validated inside the file
			else {
				staticStateManager.setCrcBytesValidated(staticStateIdentifierManager.getIdentifier(), fileId,
						fileCrc.getTotalRead());
			}

		} finally {
			IOUtils.closeQuietly(fileInputStream);
		}
	}




	private void showDif(byte[] a, byte[] b) {

		log.debug("comparing " + a + " to " + b);
		log.debug("size: " + a.length + " " + b.length);
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			if (!Byte.valueOf(a[i]).equals(Byte.valueOf(b[i]))) {
				log.debug("different byte at index " + i + " : " + Byte.valueOf(a[i]) + " " + Byte.valueOf(b[i]));
			}
		}
		if (a.length != b.length) {
			log.debug("arrays do not have a similar size so i was impossible to compare " + Math.abs(a.length - b.length) + " bytes.");
		}

	}


	@ManagedAttribute
	public long getSliceSizeInBytes() {
		return sliceSizeInBytes;
	}


	@ManagedAttribute
	public void setSliceSizeInBytes(long sliceSizeInBytes) {
		this.sliceSizeInBytes = sliceSizeInBytes;
	}



	private void resume(UUID fileId) {
		resume(fileId, uploadProcessingConfigurationManager.getUploadProcessingConfiguration(fileId));
	}

	private void resume(UUID fileId, RequestUploadProcessingConfiguration uploadProcessingConfiguration) {
		synchronized (uploadProcessingConfiguration) {
			uploadProcessingConfiguration.resume();
		}
	}

	private void pause(UUID fileId) {
		RequestUploadProcessingConfiguration uploadProcessingConfiguration = uploadProcessingConfigurationManager.getUploadProcessingConfiguration(fileId);
		synchronized (uploadProcessingConfiguration) {
			uploadProcessingConfiguration.pause();
		}
	}

}
