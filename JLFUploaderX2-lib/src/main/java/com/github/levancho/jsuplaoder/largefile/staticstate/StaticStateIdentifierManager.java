package com.github.levancho.jsuplaoder.largefile.staticstate;


import java.util.UUID;

import com.github.levancho.jsuplaoder.largefile.web.utils.RequestComponentContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.levancho.jsuplaoder.largefile.identifier.IdentifierProvider;



@Component
public class StaticStateIdentifierManager {

	@Autowired
	IdentifierProvider identifierProvider;

	@Autowired
	RequestComponentContainer requestComponentContainer;



	public UUID getIdentifier() {
		return identifierProvider.getIdentifier(requestComponentContainer.getRequest(), requestComponentContainer.getResponse());
	}


	public void clearIdentifier() {
		identifierProvider.clearIdentifier(requestComponentContainer.getRequest(), requestComponentContainer.getResponse());
	}


	public void setIdentifier(UUID id) {
		identifierProvider.setIdentifier(requestComponentContainer.getRequest(), requestComponentContainer.getResponse(), id);
	}

}
