package com.github.levancho.jsuplaoder.largefile.exception;


/**
 * Exception thrown if the uploads are not enabled at the moment.
 * @see com.github.levancho.jsuplaoder.largefile.staticstate.JavaLargeFileUploaderService#enableFileUploader()
 * @see com.github.levancho.jsuplaoder.largefile.staticstate.JavaLargeFileUploaderService#disableFileUploader()
 * @author antoinem
 */
public class UploadIsCurrentlyDisabled extends Exception {

	public UploadIsCurrentlyDisabled() {
		super("All uploads are currently suspended.");
	}
	
}
