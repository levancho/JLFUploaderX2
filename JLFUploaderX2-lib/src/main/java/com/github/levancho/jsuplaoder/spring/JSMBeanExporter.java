package com.github.levancho.jsuplaoder.spring;

import org.springframework.jmx.export.MBeanExportException;
import org.springframework.jmx.export.MBeanExporter;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;
import java.util.Map;

/**
 * Created by levancho on 8/7/2015.
 */
public class JSMBeanExporter extends MBeanExporter {
    @Override
    public void setBeans(Map<String, Object> beans) {
        super.setBeans(beans);
    }

    @Override
    protected ObjectName registerBeanNameOrInstance(Object mapValue, String beanKey) throws MBeanExportException {
        try {
            return super.registerBeanNameOrInstance(mapValue, beanKey);
        }catch (Exception e){
            System.out.println("mapValue = [" + mapValue + "], beanKey = [" + beanKey + "]");
            return null;
        }

    }

    @Override
    public ObjectName registerManagedResource(Object managedResource) throws MBeanExportException {
        return super.registerManagedResource(managedResource);
    }

    @Override
    public void registerManagedResource(Object managedResource, ObjectName objectName) throws MBeanExportException {
        super.registerManagedResource(managedResource, objectName);
    }

    @Override
    public void unregisterManagedResource(ObjectName objectName) {
        super.unregisterManagedResource(objectName);
    }

    @Override
    protected void registerBeans() {
        super.registerBeans();
    }

    @Override
    protected ModelMBean createModelMBean() throws MBeanException {
        return super.createModelMBean();
    }

    @Override
    protected void doRegister(Object mbean, ObjectName objectName) throws JMException {
        super.doRegister(mbean, objectName);
    }
}
