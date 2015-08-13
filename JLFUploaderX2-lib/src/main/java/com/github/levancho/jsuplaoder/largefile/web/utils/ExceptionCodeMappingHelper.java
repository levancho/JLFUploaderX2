package com.github.levancho.jsuplaoder.largefile.web.utils;


import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import com.github.levancho.jsuplaoder.largefile.exception.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * Contains the mapping of exception => ids
 * 
 * @author antoinem
 * @see com.github.levancho.jsuplaoder.largefile.exception.JavaFileUploaderException
 * 
 */
@Component
public class ExceptionCodeMappingHelper {

	@Autowired
	private FileUploaderHelper fileUploaderHelper;

		private static Log log = LogFactory.getLog(FileUploaderHelper.class);



	public enum ExceptionCodeMapping {

		unkownError (0),
		requestIsNotMultipart (1),
		NoFileToUploadInTheRequest (2),
		InvalidCRC (3, InvalidCrcException.class),
		MissingParameterException (4, MissingParameterException.class),
		AuthorizationException (12, com.github.levancho.jsuplaoder.largefile.exception.AuthorizationException.class),
		FileCorruptedException (14, com.github.levancho.jsuplaoder.largefile.exception.FileCorruptedException.class),
		FileStillProcessingException (15, com.github.levancho.jsuplaoder.largefile.exception.FileStillProcessingException.class),
		UploadIsCurrentlyDisabled (16, UploadIsCurrentlyDisabled.class);

		private int exceptionIdentifier;
		private Class<? extends Exception> clazz;



		private ExceptionCodeMapping(int exceptionIdentifier) {
			this.exceptionIdentifier = exceptionIdentifier;
		}


		private <T extends Exception> ExceptionCodeMapping(int exceptionIdentifier, Class<T> clazz) {
			this(exceptionIdentifier);
			this.clazz = clazz;
		}


		public int getExceptionIdentifier() {
			return exceptionIdentifier;
		}


		public void setExceptionIdentifier(int exceptionIdentifier) {
			this.exceptionIdentifier = exceptionIdentifier;
		}


	}



	public void processException(Exception e, HttpServletResponse response) {
		ExceptionCodeMapping exceptionCodeMappingByType = ExceptionCodeMappingHelper.getExceptionCodeMappingByType(e);

		// log
		if (exceptionCodeMappingByType.equals(ExceptionCodeMapping.unkownError)) {
			// with stacktrace if it is unknown
			log.error(e.getMessage(), e);
		}
		else {
			// without stracktrace if it is managed
			log.error(e.getMessage());
		}

		// write exception to response
		if (exceptionCodeMappingByType != null) {
			try {
				log.error("managed error " + exceptionCodeMappingByType.getExceptionIdentifier() + ": " + e.getMessage());
				fileUploaderHelper.writeExceptionToResponse(new JavaFileUploaderException(exceptionCodeMappingByType), response);
			}
			catch (IOException ee) {
				log.error(ee.getMessage());
			}
		}
	}


	public static ExceptionCodeMapping getExceptionCodeMappingByType(Exception e) {
		if (e instanceof JavaFileUploaderException) {
			return ((JavaFileUploaderException) e).getExceptionCodeMapping();
		}
		else {
			for (ExceptionCodeMapping exceptionsCodeMapping : ExceptionCodeMapping.values()) {
				if (exceptionsCodeMapping.clazz != null && exceptionsCodeMapping.clazz.isInstance(e)) {
					return exceptionsCodeMapping;
				}
			}
		}
		return ExceptionCodeMapping.unkownError;
	}
}
