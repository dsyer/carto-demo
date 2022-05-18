/*
 * Kubernetes
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: v1.19.11
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package io.kubernetes.client.examples.models;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * ImageStatus defines the observed state of Image
 */
@ApiModel(description = "ImageStatus defines the observed state of Image")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2022-05-18T10:06:44.759878+01:00[Europe/London]")
public class V1ImageStatus {
  public static final String SERIALIZED_NAME_COMPLETE = "complete";
  @SerializedName(SERIALIZED_NAME_COMPLETE)
  private Boolean complete;

  public static final String SERIALIZED_NAME_LATEST_IMAGE = "latestImage";
  @SerializedName(SERIALIZED_NAME_LATEST_IMAGE)
  private String latestImage;

  public static final String SERIALIZED_NAME_OBSERVED_GENERATION = "observedGeneration";
  @SerializedName(SERIALIZED_NAME_OBSERVED_GENERATION)
  private Long observedGeneration;

  public V1ImageStatus() { 
  }

  public V1ImageStatus complete(Boolean complete) {
    
    this.complete = complete;
    return this;
  }

   /**
   * Get complete
   * @return complete
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Boolean getComplete() {
    return complete;
  }


  public void setComplete(Boolean complete) {
    this.complete = complete;
  }


  public V1ImageStatus latestImage(String latestImage) {
    
    this.latestImage = latestImage;
    return this;
  }

   /**
   * Get latestImage
   * @return latestImage
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public String getLatestImage() {
    return latestImage;
  }


  public void setLatestImage(String latestImage) {
    this.latestImage = latestImage;
  }


  public V1ImageStatus observedGeneration(Long observedGeneration) {
    
    this.observedGeneration = observedGeneration;
    return this;
  }

   /**
   * Get observedGeneration
   * @return observedGeneration
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")

  public Long getObservedGeneration() {
    return observedGeneration;
  }


  public void setObservedGeneration(Long observedGeneration) {
    this.observedGeneration = observedGeneration;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    V1ImageStatus v1ImageStatus = (V1ImageStatus) o;
    return Objects.equals(this.complete, v1ImageStatus.complete) &&
        Objects.equals(this.latestImage, v1ImageStatus.latestImage) &&
        Objects.equals(this.observedGeneration, v1ImageStatus.observedGeneration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(complete, latestImage, observedGeneration);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class V1ImageStatus {\n");
    sb.append("    complete: ").append(toIndentedString(complete)).append("\n");
    sb.append("    latestImage: ").append(toIndentedString(latestImage)).append("\n");
    sb.append("    observedGeneration: ").append(toIndentedString(observedGeneration)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

