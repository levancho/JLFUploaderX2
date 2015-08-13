package com.github.levancho.jsuplaoder.largefile.exception;


import com.github.levancho.jsuplaoder.largefile.web.utils.ExceptionCodeMappingHelper;


/**
 * This exception contains a simple identifier ({@link #exceptionIdentifier}) so that the javascript
 * can identify it and i18n it.
 * 
 * @author antoinem
 * 
 */
public class JavaFileUploaderException extends Exception {

	private ExceptionCodeMappingHelper.ExceptionCodeMapping exceptionCodeMapping;



	public JavaFileUploaderException() {
	}


	public JavaFileUploaderException(ExceptionCodeMappingHelper.ExceptionCodeMapping exceptionCodeMapping) {
		super();
		this.exceptionCodeMapping = exceptionCodeMapping;
	}


	public ExceptionCodeMappingHelper.ExceptionCodeMapping getExceptionCodeMapping() {
		return exceptionCodeMapping;
	}


	public void setExceptionCodeMapping(ExceptionCodeMappingHelper.ExceptionCodeMapping exceptionCodeMapping) {
		this.exceptionCodeMapping = exceptionCodeMapping;
	}


}
