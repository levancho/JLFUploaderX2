package com.github.levancho.jsuplaoder.largefile.web;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.levancho.jsuplaoder.largefile.authorizer.Authorizer;
import com.github.levancho.jsuplaoder.largefile.web.utils.ExceptionCodeMappingHelper;
import com.github.levancho.jsuplaoder.largefile.exception.UploadIsCurrentlyDisabled;
import com.github.levancho.jsuplaoder.largefile.logic.UploadServletAsyncProcessor;
import com.github.levancho.jsuplaoder.largefile.web.utils.FileUploadConfiguration;
import com.github.levancho.jsuplaoder.largefile.web.utils.FileUploaderHelper;
import com.github.levancho.jsuplaoder.largefile.staticstate.StaticStateIdentifierManager;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticStatePersistedOnFileSystemEntity;
import org.apache.commons.lang.time.DateUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.support.HttpRequestHandlerServlet;

import com.github.levancho.jsuplaoder.largefile.staticstate.StaticStateManager;
import com.github.levancho.jsuplaoder.largefile.staticstate.entities.StaticFileState;


@Component("javaLargeFileUploaderAsyncServlet")
@WebServlet(name = "javaLargeFileUploaderAsyncServlet", urlPatterns = { "/uploaderx2/javaLargeFileUploaderAsyncServlet" }, asyncSupported = true)
public class UploadServletAsync extends HttpRequestHandlerServlet
		implements HttpRequestHandler {

	private static Log log = LogFactory.getLog(UploadServletAsync.class);

	@Autowired
	ExceptionCodeMappingHelper exceptionCodeMappingHelper;

	@Autowired
	UploadServletAsyncProcessor uploadServletAsyncProcessor;
	
	@Autowired
	StaticStateIdentifierManager staticStateIdentifierManager;

	@Autowired
	StaticStateManager<StaticStatePersistedOnFileSystemEntity> staticStateManager;

	@Autowired
	FileUploaderHelper fileUploaderHelper;

	@Autowired
	Authorizer authorizer;

	/**
	 * Maximum time that a streaming request can take.<br>
	 */
	private long taskTimeOut = DateUtils.MILLIS_PER_HOUR;


	@Override
	public void handleRequest(final HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {

		// process the request
		try {

			//check if uploads are allowed
			if (!uploadServletAsyncProcessor.isEnabled()) {
				throw new UploadIsCurrentlyDisabled();
			}
			
			// extract stuff from request
			final FileUploadConfiguration process = fileUploaderHelper.extractFileUploadConfiguration(request);

			log.debug("received upload request with config: "+process);

			// verify authorization
			final UUID clientId = staticStateIdentifierManager.getIdentifier();
			authorizer.getAuthorization(request, UploadServletAction.upload, clientId, process.getFileId());

			//check if that file is not paused
			if (uploadServletAsyncProcessor.isFilePaused(process.getFileId())) {
				log.debug("file "+process.getFileId()+" is paused, ignoring async request.");
				return;
			}
			
			// get the model
			StaticFileState fileState = staticStateManager.getEntityIfPresent().getFileStates().get(process.getFileId());
			if (fileState == null) {
				throw new FileNotFoundException("File with id " + process.getFileId() + " not found");
			}

			// process the request asynchronously
			final AsyncContext asyncContext = request.startAsync();
			asyncContext.setTimeout(taskTimeOut);


			// add a listener to clear bucket and close inputstream when process is complete or
			// with
			// error
			asyncContext.addListener(new UploadServletAsyncListenerAdapter(process.getFileId()) {

				@Override
				void clean() {
					log.debug("request " + request + " completed.");
					// we do not need to clear the inputstream here.
					// and tell processor to clean its shit!
					uploadServletAsyncProcessor.clean(clientId, process.getFileId());
				}
			});

			// then process
			uploadServletAsyncProcessor.process(fileState, process.getFileId(), process.getCrc(), process.getInputStream(),
					new UploadServletAsyncProcessor.WriteChunkCompletionListener() {

						@Override
						public void success() {
							asyncContext.complete();
						}


						@Override
						public void error(Exception exception) {
							// handles a stream ended unexpectedly , it just means the user has
							// stopped the
							// stream
							if (exception.getMessage() != null) {
								if (exception.getMessage().equals("Stream ended unexpectedly")) {
									log.warn("User has stopped streaming for file " + process.getFileId());
								}
								else if (exception.getMessage().equals("User cancellation")) {
									log.warn("User has cancelled streaming for file id " + process.getFileId());
									// do nothing
								}
								else {
									exceptionCodeMappingHelper.processException(exception, response);
								}
							}
							else {
								exceptionCodeMappingHelper.processException(exception, response);
							}

							asyncContext.complete();
						}

					});
		}
		catch (Exception e) {
			exceptionCodeMappingHelper.processException(e, response);
		}

	}

}
