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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * ImageSpec defines the desired state of Image
 */
@ApiModel(description = "ImageSpec defines the desired state of Image")
@JsonPropertyOrder({
  V1ImageSpec.JSON_PROPERTY_IMAGE,
  V1ImageSpec.JSON_PROPERTY_INTERVAL
})
@JsonTypeName("v1_Image_spec")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2022-05-18T09:42:11.039198+01:00[Europe/London]")
public class V1ImageSpec {
  public static final String JSON_PROPERTY_IMAGE = "image";
  private String image;

  public static final String JSON_PROPERTY_INTERVAL = "interval";
  private String interval;

  public V1ImageSpec() { 
  }

  public V1ImageSpec image(String image) {
    
    this.image = image;
    return this;
  }

   /**
   * A registry image path, e.g. \&quot;nginx\&quot; or \&quot;gcr.io/my-project/app\&quot;
   * @return image
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "A registry image path, e.g. \"nginx\" or \"gcr.io/my-project/app\"")
  @JsonProperty(JSON_PROPERTY_IMAGE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getImage() {
    return image;
  }


  @JsonProperty(JSON_PROPERTY_IMAGE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setImage(String image) {
    this.image = image;
  }


  public V1ImageSpec interval(String interval) {
    
    this.interval = interval;
    return this;
  }

   /**
   * The interval at which to check for repository updates, e.g. \&quot;30s\&quot; or \&quot;1m30s\&quot;.
   * @return interval
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "The interval at which to check for repository updates, e.g. \"30s\" or \"1m30s\".")
  @JsonProperty(JSON_PROPERTY_INTERVAL)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getInterval() {
    return interval;
  }


  @JsonProperty(JSON_PROPERTY_INTERVAL)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setInterval(String interval) {
    this.interval = interval;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    V1ImageSpec v1ImageSpec = (V1ImageSpec) o;
    return Objects.equals(this.image, v1ImageSpec.image) &&
        Objects.equals(this.interval, v1ImageSpec.interval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(image, interval);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class V1ImageSpec {\n");
    sb.append("    image: ").append(toIndentedString(image)).append("\n");
    sb.append("    interval: ").append(toIndentedString(interval)).append("\n");
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

