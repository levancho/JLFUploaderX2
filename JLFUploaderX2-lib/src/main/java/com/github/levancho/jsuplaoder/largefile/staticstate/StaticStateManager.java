package com.github.levancho.jsuplaoder.largefile.staticstate;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.github.levancho.jsuplaoder.largefile.exception.FileCorruptedException;
import com.github.levancho.jsuplaoder.largefile.notifier.JLFUListenerPropagator;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticFileState;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticStatePersistedOnFileSystemEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.levancho.jsuplaoder.largefile.json.FileStateJsonBase;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.thoughtworks.xstream.XStream;



/**
 * Manages the information related to the files.<br>
 * This information is stored locally in a cache and also persisted on the file system.<br>
 * All of the methods used here are to be called within the scope of a request. Most of these methods (to query and update) are also available outside of such a scope in {@link JavaLargeFileUploaderService}
 * This class has to be initialized with the {@link #init(Class)} method first.
 * 
 * @author antoinem
 * 
 * @param <T>
 */
@Component
public class StaticStateManager<T extends StaticStatePersistedOnFileSystemEntity> {

	private static Log log = LogFactory.getLog(StaticStateManager.class);


	static final String FILENAME = "StaticState.xml";

	@Autowired
	FileDeleter fileDeleter;

	@Autowired
	JLFUListenerPropagator uploadxListenerPropagator;

	@Autowired
	StaticStateIdentifierManager staticStateIdentifierManager;

	@Autowired
	StaticStateDirectoryManager staticStateDirectoryManager;

	@Autowired
	JavaLargeFileUploaderService<T> staticStateManagerService;

	/**
	 * Used to bypass generic type erasure.<br>
	 * Has to be manually specified with the {@link #init(Class)} method.
	 */
	Class<T> entityType;


	/** The executor that could write stuff asynchronously into the static state */
	private ThreadPoolExecutor fileStateUpdaterExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);



	private Class<T> getEntityType() {
		// if not defined, try to init with default
		if (entityType == null) {
			entityType = (Class<T>) StaticStatePersistedOnFileSystemEntity.class;
		}
		return entityType;
	}



	LoadingCache<UUID, T> cache = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.DAYS).build(new CacheLoader<UUID, T>() {

		public T load(UUID uuid)
				throws Exception {

			return createOrRestore(uuid);

		}
	});



	/**
	 * Creates or restores a new file.
	 * 
	 * @param uuid
	 * @return
	 * @throws java.io.IOException
	 */
	private T createOrRestore(UUID uuid)
			throws IOException {

		// restore cache from file:
		File uuidFileParent = staticStateDirectoryManager.getUUIDFileParent();

		// if that file is scheduled for deletion, we do not restore it
		if (fileDeleter.deletionQueueContains(uuidFileParent)) {
			log.debug("trying to restore from state a file that is scheduled for deletion");
			// invalidate identifier
			staticStateIdentifierManager.clearIdentifier();
			// get a new one
			// and recreate file
			uuidFileParent = staticStateDirectoryManager.getUUIDFileParent();
		}

		File uuidFile = new File(uuidFileParent, FILENAME);
		T entity = null;

		if (uuidFile.exists()) {
			log.debug("No value in the cache for uuid " + uuid + ". Filling cache from file.");
			try {
				entity = read(uuidFile);
			}
			catch (Exception e) {
				log.error("Cache cannot be restored from " + uuidFile.getAbsolutePath() + "." +
						"The file might be empty or the model has changed since last time: " + e.getMessage(), e);
			}
		}
		else {
			log.debug("No value in the cache for uuid " + uuid + " and no value in the file. Creating a new one.");

			// create the file
			try {
				uuidFile.createNewFile();
			}
			catch (IOException e) {
				log.error("cannot create model file: " + e.getMessage(), e);
				throw e;
			}

			// and persist an entity
			try {
				entity = getEntityType().newInstance();
			}
			catch (InstantiationException e) {
				throw new RuntimeException(e);
			}
			catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			staticStateManagerService.writeEntity(uuidFile, entity);

		}

		// then return entity
		return entity;
	}

	/**
	 * Retrieves the entity from cookie or cache if it exists or create one if it does not exists.
	 * 
	 * @return
	 * @throws java.util.concurrent.ExecutionException
	 */
	public T getEntity() {
		return cache.getUnchecked(staticStateIdentifierManager.getIdentifier());
	}


	/**
	 * Retrieves the entity from cache or null if this entity is not present.
	 * 
	 * @return
	 */
	public StaticStatePersistedOnFileSystemEntity getEntityIfPresent() {
		return staticStateManagerService.getEntityIfPresent(staticStateIdentifierManager.getIdentifier());
	}


	/**
	 * Persist modifications to file and cache.
	 * 
	 * @param entity
	 * @return
	 * @throws java.util.concurrent.ExecutionException
	 */
	public void updateEntity(T entity) {
		UUID uuid = staticStateIdentifierManager.getIdentifier();
		staticStateManagerService.updateEntity(uuid, entity);
	}


	/**
	 * Clear everything including cache, session, files.
	 * 
	 * @throws java.util.concurrent.TimeoutException
	 * @throws java.util.concurrent.ExecutionException
	 * @throws InterruptedException
	 */
	public void clear()
	{
		//clear stuff on the server
		staticStateManagerService.clearClient(staticStateIdentifierManager.getIdentifier());
		
		// remove cookie and session
		staticStateIdentifierManager.clearIdentifier();

	}

	
	public void clearFile(final UUID fileId)
	{
		staticStateManagerService.clearFile(staticStateIdentifierManager.getIdentifier(), fileId);
	}



	T read(File f) {
		XStream xStream = new XStream();
		FileInputStream fs = null;
		T fromXML = null;
		try {
			fs = new FileInputStream(f);
			fromXML = (T) xStream.fromXML(fs);
		}
		catch (FileNotFoundException e) {
			log.error("cannot read model file: " + e.getMessage(), e);
		}
		finally {
			IOUtils.closeQuietly(fs);
		}
		return fromXML;
	}


	/**
	 * Initializes the bean with the class of the entity. Shall be called once. Calling it more than
	 * once has no effect.
	 * 
	 * @param clazz
	 */
	public void init(Class<T> clazz) {
		entityType = clazz;
	}


	/**
	 * Writes in the file that the last slice has been successfully uploaded.
	 * 
	 * @param clientId
	 * @param fileId
	 * @return true if the file is complete
	 * @throws com.github.levancho.jsuplaoder.largefile.exception.FileCorruptedException
	 */
	public void setCrcBytesValidated(final UUID clientId, UUID fileId, final long validated) throws FileCorruptedException {

		final T entity = cache.getIfPresent(clientId);
		if (entity == null) {
			return;
		}
		final StaticFileState staticFileState = entity.getFileStates().get(fileId);
		if (staticFileState == null) {
			return;
		}
		FileStateJsonBase staticFileStateJson = staticFileState.getStaticFileStateJson();
		if (staticFileStateJson == null) {
			return;
		}
		Long crcredBytes = staticFileStateJson.getCrcedBytes();
		staticFileStateJson.setCrcedBytes(
				crcredBytes + validated);

		log.debug(validated + " more bytes have been validated appended to the already " + crcredBytes + " bytes validated for file " + fileId +
				" for client id " + clientId);

		// manage the end of file
		if (staticFileStateJson.getCrcedBytes().equals(staticFileStateJson.getOriginalFileSizeInBytes())) {
			uploadxListenerPropagator.getPropagator().onFileUploadEnd(clientId, fileId);
		}

		//checks whether we have a file corruption exception
		if (staticFileStateJson.getCrcedBytes() > staticFileStateJson.getOriginalFileSizeInBytes()) {
			throw new FileCorruptedException(staticFileStateJson.getCrcedBytes() + " crced bytes are more than it should be: " + staticFileStateJson.getOriginalFileSizeInBytes());
		}

		fileStateUpdaterExecutor.submit(new Runnable() {

			@Override
			public void run() {
				// write this later on.
				staticStateManagerService.writeEntity(clientId, entity);
			}
		});

	}

}
