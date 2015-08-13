package com.github.levancho.jsuplaoder.largefile.exception;


import com.github.levancho.jsuplaoder.largefile.web.UploadServletParameter;


public class MissingParameterException extends BadRequestException {

	public MissingParameterException(UploadServletParameter parameter) {
		super("The parameter " + parameter.name() + " is missing for this request.");
	}


}
