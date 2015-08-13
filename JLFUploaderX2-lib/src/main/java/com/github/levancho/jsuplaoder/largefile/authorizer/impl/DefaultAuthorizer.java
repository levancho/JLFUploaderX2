package com.github.levancho.jsuplaoder.largefile.authorizer.impl;


import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import com.github.levancho.jsuplaoder.largefile.authorizer.Authorizer;
import com.github.levancho.jsuplaoder.largefile.web.UploadServletAction;
import org.springframework.stereotype.Component;


/**
 * Default {@link com.github.levancho.jsuplaoder.largefile.authorizer.Authorizer} that never throws an {@link com.github.levancho.jsuplaoder.largefile.exception.AuthorizationException}.
 * 
 * @author antoinem
 * 
 */
@Component
public class DefaultAuthorizer
		implements Authorizer {

	@Override
	public void getAuthorization(HttpServletRequest request, UploadServletAction action, UUID clientId, UUID... optionalFileId) {
		// by default, all calls are authorized
	}

}
