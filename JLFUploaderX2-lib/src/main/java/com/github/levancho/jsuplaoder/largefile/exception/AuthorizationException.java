package com.github.levancho.jsuplaoder.largefile.exception;


import java.util.Arrays;
import java.util.UUID;

import com.github.levancho.jsuplaoder.largefile.web.UploadServletAction;



public class AuthorizationException extends Exception {

	public AuthorizationException(UploadServletAction actionByParameterName, UUID clientId, UUID... optionalFileIds) {
		super("User " + clientId + " is not authorized to perform " + actionByParameterName + (optionalFileIds != null ? " on " + Arrays.toString(optionalFileIds) : ""));
	}


}
