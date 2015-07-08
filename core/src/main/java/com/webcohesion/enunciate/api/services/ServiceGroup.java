package com.webcohesion.enunciate.api.services;

import java.io.File;
import java.util.List;

/**
 * @author Ryan Heaton
 */
public class ServiceGroup {

  private String namespace;
  private File wsdlFile;
  private List<? extends Service> services;

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public File getWsdlFile() {
    return wsdlFile;
  }

  public void setWsdlFile(File wsdlFile) {
    this.wsdlFile = wsdlFile;
  }

  public List<? extends Service> getServices() {
    return services;
  }

  public void setServices(List<? extends Service> services) {
    this.services = services;
  }
}