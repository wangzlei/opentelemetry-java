/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.extensions.trace.aws.resource;

import static io.opentelemetry.sdk.extensions.trace.aws.resource.EksResource.AUTH_CONFIGMAP_PATH;
import static io.opentelemetry.sdk.extensions.trace.aws.resource.EksResource.CW_CONFIGMAP_PATH;
import static io.opentelemetry.sdk.extensions.trace.aws.resource.EksResource.K8S_SVC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import io.opentelemetry.sdk.resources.ResourceProvider;
import java.io.File;
import java.io.IOException;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EksResourceTest {

  @Mock private DockerHelper mockDockerHelper;

  @Mock private JdkHttpClient jdkHttpClient;

  @Test
  void testEks(@TempDir File tempFolder) throws IOException {
    File mockK8sTokenFile = new File(tempFolder, "k8sToken");
    String token = "token123";
    Files.write(token.getBytes(Charsets.UTF_8), mockK8sTokenFile);
    File mockK8sKeystoreFile = new File(tempFolder, "k8sCert");
    String truststore = "truststore123";
    Files.write(truststore.getBytes(Charsets.UTF_8), mockK8sKeystoreFile);

    when(jdkHttpClient.fetchString(
            any(), Mockito.eq(K8S_SVC_URL + AUTH_CONFIGMAP_PATH), any(), any()))
        .thenReturn("not empty");
    when(jdkHttpClient.fetchString(
            any(), Mockito.eq(K8S_SVC_URL + CW_CONFIGMAP_PATH), any(), any()))
        .thenReturn("{\"data\":{\"cluster.name\":\"my-cluster\"}}");
    when(mockDockerHelper.getContainerId()).thenReturn("0123456789A");

    EksResource eksResource =
        new EksResource(
            jdkHttpClient,
            mockDockerHelper,
            mockK8sTokenFile.getPath(),
            mockK8sKeystoreFile.getPath());
    Attributes attributes = eksResource.getAttributes();

    assertThat(attributes)
        .isEqualTo(
            Attributes.of(
                ResourceAttributes.K8S_CLUSTER, "my-cluster",
                ResourceAttributes.CONTAINER_ID, "0123456789A"));
  }

  @Test
  void testNotEks() {
    EksResource eksResource = new EksResource(jdkHttpClient, mockDockerHelper, "", "");
    Attributes attributes = eksResource.getAttributes();
    assertThat(attributes.isEmpty()).isTrue();
  }

  @Test
  void inServiceLoader() {
    // No practical way to test the attributes themselves so at least check the service loader picks
    // it up.
    assertThat(ServiceLoader.load(ResourceProvider.class)).anyMatch(EksResource.class::isInstance);
  }
}
