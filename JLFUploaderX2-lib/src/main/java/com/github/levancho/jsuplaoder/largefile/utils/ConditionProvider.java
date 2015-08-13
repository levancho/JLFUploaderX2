package com.github.levancho.jsuplaoder.largefile.utils;


public abstract class ConditionProvider {

	public abstract boolean condition();


	public void onFail() {
	}


	public void onSuccess() {

	}
}
