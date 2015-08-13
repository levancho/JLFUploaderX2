package com.github.levancho.jsuplaoder.largefile.notifier;


import org.springframework.stereotype.Component;

import com.github.levancho.jsuplaoder.largefile.notifier.utils.GenericPropagator;



/**
 * Propagates the events to the registered listeners.
 * 
 * @author antoinem
 * 
 */
@Component
public class JLFUListenerPropagator extends GenericPropagator<JLFUListener> {

	@Override
	protected Class<JLFUListener> getProxiedClass() {
		return JLFUListener.class;
	}

}
