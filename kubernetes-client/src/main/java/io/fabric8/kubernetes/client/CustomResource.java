/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.utils.Pluralize;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.sundr.builder.annotations.Buildable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.kubernetes.client.utils.Utils.isNullOrEmpty;

/**
 * A base class for implementing a custom resource kind
 *
 * @param <S> the class providing the {@code Spec} part of this CustomResource
 * @param <T> the class providing the {@code Status} part of this CustomResource
 */
@JsonDeserialize(
  using = JsonDeserializer.None.class
)
@JsonPropertyOrder({
  "apiVersion",
  "kind",
  "metadata",
  "spec",
  "status"
})
@Buildable(builderPackage = "io.fabric8.kubernetes.api.builder", editableEnabled = false)
public abstract class CustomResource<S extends KubernetesResource, T extends KubernetesResource> implements HasMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(CustomResource.class);
  
  public static final String NAMESPACE_SCOPE = "Namespaced";
  public static final String CLUSTER_SCOPE = "Cluster";
  private ObjectMeta metadata = new ObjectMeta();
  
  @JsonProperty("spec")
  private S spec;
  
  @JsonProperty("status")
  private T status;
  
  @JsonIgnore
  private String plural;
  
  @JsonIgnore
  private String singular;
  
  @JsonIgnore
  private String crdName;
  
  private final String kind;
  private final String apiVersion;
  private final String scope;
  
  public CustomResource() {
    final String version = HasMetadata.super.getApiVersion();
    if (isNullOrEmpty(version)) {
      throw new IllegalArgumentException(getClass().getName() + " CustomResource must provide an API version using @ApiGroup and @ApiVersion annotations");
    }
    this.apiVersion = version;
    this.kind = HasMetadata.super.getKind();
    scope = this instanceof Namespaced ? NAMESPACE_SCOPE : CLUSTER_SCOPE;
  }
  
  @Override
  public String toString() {
    return "CustomResource{" +
      "kind='" + getKind() + '\'' +
      ", apiVersion='" + getApiVersion() + '\'' +
      ", metadata=" + metadata +
      ", spec=" + spec +
      ", status=" + status +
      '}';
  }
  
  @Override
  public String getApiVersion() {
    return apiVersion;
  }
  
  @Override
  public void setApiVersion(String version) {
    // already set in constructor
    LOG.warn("Calling CustomResource#setApiVersion doesn't do anything because the API version is computed and shouldn't be changed");
  }
  
  @Override
  public String getKind() {
    return this.kind;
  }
  
  public void setKind(String kind) {
    // already set in constructor
    LOG.warn("Calling CustomResource#setKind doesn't do anything because the Kind is computed and shouldn't be changed");
  }
  
  @Override
  public ObjectMeta getMetadata() {
    return metadata;
  }
  
  @Override
  public void setMetadata(ObjectMeta metadata) {
    this.metadata = metadata;
  }
  
  /**
   * Retrieves the plural form associated with the specified CustomResource if annotated with {@link Plural} or computes a default value
   * using the value returned by {@link #getSingular(Class)} as input to {@link Pluralize#toPlural(String)}.
   *
   * @param clazz the CustomResource whose plural form we want to retrieve
   * @return the plural form defined by the {@link Plural} annotation or a computed default value
   */
  public static String getPlural(Class<? extends CustomResource> clazz) {
    final Plural fromAnnotation = clazz.getAnnotation(Plural.class);
    return (fromAnnotation != null ? fromAnnotation.value().toLowerCase(Locale.ROOT) : Pluralize.toPlural(getSingular(clazz)));
  }
  
  @JsonIgnore
  public String getPlural() {
    if (plural == null) {
      this.plural = getPlural(getClass());
    }
    return plural;
  }
  
  /**
   * Retrieves the singular form associated with the specified CustomResource as defined by the {@link Singular} annotation or
   * computes a default value (lower-cased version of the value returned by {@link HasMetadata#getKind(Class)}) if the annotation
   * is not present.
   *
   * @param clazz the CustomeResource whose singular form we want to retrieve
   * @return the singular form defined by the {@link Singular} annotation or a computed default value
   */
  public static String getSingular(Class<? extends CustomResource> clazz) {
    final Singular fromAnnotation = clazz.getAnnotation(Singular.class);
    return (fromAnnotation != null ? fromAnnotation.value() : HasMetadata.getKind(clazz)).toLowerCase(Locale.ROOT);
  }
  
  /**
   * Calls {@link CustomResource#getSingular(Class)} passing this instance's class as parameter
   *
   * @return the singular form associated with this CustomResource
   */
  @JsonIgnore
  public String getSingular() {
    if (singular == null) {
      this.singular = getSingular(getClass());
    }
    return singular;
  }
  
  /**
   * Computes the name of the Custom Resource Definition (CRD) associated with the specified CustomResource.
   * See https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/ for more details.
   *
   * @param clazz the CustomResource whose CRD name we want to compute
   * @return the CRD name associated with the CustomResource
   */
  public static String getCRDName(Class<? extends CustomResource> clazz) {
    return getPlural(clazz) + "." + HasMetadata.getGroup(clazz);
  }
  
  /**
   * Calls {@link CustomResource#getCRDName(Class)} passing this instance's class as parameter
   *
   * @return the CRD name associated with this CustomResource
   */
  @JsonIgnore
  public String getCRDName() {
    if (crdName == null) {
      this.crdName = getCRDName(getClass());
    }
    return crdName;
  }
  
  /**
   * Retrieves the scope that this CustomResource targets
   *
   * @return the scope that this CustomResource targets. Possible values are {@link #CLUSTER_SCOPE} or {@link #NAMESPACE_SCOPE}.
   */
  @JsonIgnore
  public String getScope() {
    return scope;
  }
  
  /**
   * Calls {@link HasMetadata#getGroup(Class)} passing this instance's class as parameter
   *
   * @return the group associated with this CustomResource
   */
  @JsonIgnore
  public String getGroup() {
    return HasMetadata.getGroup(getClass());
  }
  
  /**
   * Calls {@link HasMetadata#getVersion(Class)} passing this instance's class as parameter
   *
   * @return the version associated with this CustomResource
   */
  @JsonIgnore
  public String getVersion() {
    return HasMetadata.getVersion(getClass());
  }
  
  public S getSpec() {
    return spec;
  }
  
  public void setSpec(S spec) {
    this.spec = spec;
  }
  
  public T getStatus() {
    return status;
  }
  
  public void setStatus(T status) {
    this.status = status;
  }
}
