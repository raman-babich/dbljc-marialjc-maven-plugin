/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ramanbabich.dbljc.marialjcmavenplugin;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

/**
 * @author Raman Babich
 */
class GenerateMojoTest {

  private static final String THIS_PLUGIN_KEY = "com.ramanbabich.dbljc:marialjc-maven-plugin";
  private static final String MARIA_DRIVER_NAME = "org.mariadb.jdbc.Driver";
  private static final String JOOQ_MARIA_META = "org.jooq.meta.mariadb.MariaDBDatabase";
  private static final String LIQUIBASE_CONFIGURATION_ROOT_ELEMENT_NAME = "liquibaseConfiguration";
  private static final String JOOQ_CONFIGURATION_ROOT_ELEMENT_NAME = "jooqConfiguration";
  private static final String DEFAULT_ROOT_ELEMENT_NAME = "configuration";

  private static final String MARIA_DOCKER_IMAGE_NAME = "mariaDockerImageName";
  private static final String MARIA_JDBC_DRIVER_VERSION = "mariaJdbcDriverVersion";
  private static final String LIQUIBASE_MAVEN_PLUGIN_VERSION = "liquibaseMavenPluginVersion";
  private static final String JOOQ_CODEGEN_MAVEN_PLUGIN_VERSION = "jooqCodegenMavenPluginVersion";
  private static final String LIQUIBASE_MAVEN_PLUGIN_GOAL = "update";
  private static final String JOOQ_CODEGEN_MAVEN_PLUGIN_GOAL = "generate";

  private final MavenProject mavenProject = Mockito.mock(MavenProject.class);
  private final MavenSession mavenSession = Mockito.mock(MavenSession.class);
  private final BuildPluginManager buildPluginManager = Mockito.mock(BuildPluginManager.class);

  @Test
  @SuppressWarnings("rawtypes")
  void shouldGenerate() throws Exception {
    GenerateMojo mojo = buildMojoWithMocks();
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.doReturn(plugin).when(mavenProject).getPlugin(THIS_PLUGIN_KEY);
    Xpp3Dom configuration = Xpp3DomBuilder.build(new ByteArrayInputStream("""
            <configuration>
              <mariaDockerImageName>${maria.docker-image}</mariaDockerImageName>
              <mariaJdbcDriverVersion>${maria.version}</mariaJdbcDriverVersion>
              <liquibaseMavenPluginVersion>${liquibase.version}</liquibaseMavenPluginVersion>
              <jooqCodegenMavenPluginVersion>${jooq.version}</jooqCodegenMavenPluginVersion>
              <liquibaseConfiguration>
                <changeLogFile>/com/ramanbabich/dbljc/marialjcmavenplugin/liquibase/changelog/db.changelog-master.yaml</changeLogFile>
                <contexts>default</contexts>
              </liquibaseConfiguration>
              <jooqConfiguration>
                <generator>
                  <database>
                    <inputSchema>input_schema</inputSchema>
                  </database>
                  <target>
                    <packageName>com.ramanbabich.dbljc.marialjcmavenplugin.jooq</packageName>
                  </target>
                </generator>
              </jooqConfiguration>
            </configuration>
            """.getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8.name());
    Mockito.doReturn(configuration).when(plugin).getConfiguration();

    String jdbcUrl = "jdbcUrl";
    String username = "username";
    String password = "password";
    try (MockedConstruction<MariaDBContainer> mariaMockedConstruction =
        Mockito.mockConstruction(MariaDBContainer.class, (mock, context) -> {
          Mockito.doReturn(jdbcUrl).when(mock).getJdbcUrl();
          Mockito.doReturn(username).when(mock).getUsername();
          Mockito.doReturn(password).when(mock).getPassword();
        });
        MockedStatic<MojoExecutor> mojoExecutor = Mockito.mockStatic(MojoExecutor.class)) {
      mojoExecutor.when(() -> MojoExecutor.goal(LIQUIBASE_MAVEN_PLUGIN_GOAL))
          .thenReturn(LIQUIBASE_MAVEN_PLUGIN_GOAL);
      mojoExecutor.when(() -> MojoExecutor.goal(JOOQ_CODEGEN_MAVEN_PLUGIN_GOAL))
          .thenReturn(JOOQ_CODEGEN_MAVEN_PLUGIN_GOAL);
      ExecutionEnvironment executionEnvironment = Mockito.mock(ExecutionEnvironment.class);
      mojoExecutor.when(() ->
          MojoExecutor.executionEnvironment(mavenProject, mavenSession, buildPluginManager))
              .thenReturn(executionEnvironment);

      mojo.execute();

      Assertions.assertEquals(1, mariaMockedConstruction.constructed().size());
      MariaDBContainer maria = mariaMockedConstruction.constructed().get(0);
      Mockito.verify(maria).start();
      Mockito.verify(maria, Mockito.times(2)).getJdbcUrl();
      Mockito.verify(maria, Mockito.times(2)).getUsername();
      Mockito.verify(maria, Mockito.times(2)).getPassword();
      Mockito.verify(maria).close();

      mojoExecutor.verify(() ->
          MojoExecutor.executeMojo(
              Mockito.argThat(actualPlugin ->
                  eqByCrucialFields(actualPlugin, liquibaseMavenPlugin(
                      LIQUIBASE_MAVEN_PLUGIN_VERSION, MARIA_JDBC_DRIVER_VERSION))),
              Mockito.eq(LIQUIBASE_MAVEN_PLUGIN_GOAL),
              Mockito.eq(Xpp3DomBuilder.build(new ByteArrayInputStream(String.format("""
              <configuration>
                <changeLogFile>/com/ramanbabich/dbljc/marialjcmavenplugin/liquibase/changelog/db.changelog-master.yaml</changeLogFile>
                <contexts>default</contexts>
                <driver>%s</driver>
                <url>%s</url>
                <username>%s</username>
                <password>%s</password>
              </configuration>
              """, MARIA_DRIVER_NAME, jdbcUrl, username, password)
                      .getBytes(StandardCharsets.UTF_8)),
                  StandardCharsets.UTF_8.name())),
              Mockito.eq(executionEnvironment)));
      mojoExecutor.verify(() ->
          MojoExecutor.executeMojo(
              Mockito.argThat(actualPlugin ->
                      eqByCrucialFields(actualPlugin, jooqCodegenMavenPlugin(
                  JOOQ_CODEGEN_MAVEN_PLUGIN_VERSION, MARIA_JDBC_DRIVER_VERSION))),
              Mockito.eq(JOOQ_CODEGEN_MAVEN_PLUGIN_GOAL),
              Mockito.eq(Xpp3DomBuilder.build(new ByteArrayInputStream(String.format("""
              <configuration>
                <generator>
                  <database>
                    <inputSchema>input_schema</inputSchema>
                    <name>%s</name>
                  </database>
                  <target>
                    <packageName>com.ramanbabich.dbljc.marialjcmavenplugin.jooq</packageName>
                  </target>
                </generator>
                <jdbc>
                  <driver>%s</driver>
                  <url>%s</url>
                  <username>%s</username>
                  <password>%s</password>
                </jdbc>
              </configuration>
              """, JOOQ_MARIA_META, MARIA_DRIVER_NAME, jdbcUrl, username, password)
                      .getBytes(StandardCharsets.UTF_8)),
                  StandardCharsets.UTF_8.name())),
              Mockito.eq(executionEnvironment)));
    }
  }

  private static boolean eqByCrucialFields(Plugin actual, Plugin expected) {
    if (actual.getGroupId().equals(expected.getGroupId())
        && actual.getArtifactId().equals(expected.getArtifactId())
        && actual.getVersion().equals(expected.getVersion())
        && actual.getDependencies().size() == expected.getDependencies().size()) {
      // comparison with strict order for simplicity
      for (int i = 0; i < actual.getDependencies().size(); ++i) {
        if (!eqByCrucialFields(
            actual.getDependencies().get(i), expected.getDependencies().get(i))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean eqByCrucialFields(Dependency actual, Dependency expected) {
    return actual.getGroupId().equals(expected.getGroupId())
        && actual.getArtifactId().equals(expected.getArtifactId())
        && actual.getVersion().equals(expected.getVersion());
  }

  private static Plugin liquibaseMavenPlugin(String liquibaseMavenPluginVersion,
      String mariaJdbcDriverVersion) {
    Plugin plugin = new Plugin();
    plugin.setGroupId("org.liquibase");
    plugin.setArtifactId("liquibase-maven-plugin");
    plugin.setVersion(liquibaseMavenPluginVersion);
    plugin.setDependencies(List.of(mariaJdbcDriverDependency(mariaJdbcDriverVersion)));
    return plugin;
  }

  private static Dependency mariaJdbcDriverDependency(String version) {
    Dependency dependency = new Dependency();
    dependency.setGroupId("org.mariadb.jdbc");
    dependency.setArtifactId("mariadb-java-client");
    dependency.setVersion(version);
    return dependency;
  }

  private static Plugin jooqCodegenMavenPlugin(String jooqCodegenMavenPluginVersion,
      String mariaJdbcDriverVersion) {
    Plugin plugin = new Plugin();
    plugin.setGroupId("org.jooq");
    plugin.setArtifactId("jooq-codegen-maven");
    plugin.setVersion(jooqCodegenMavenPluginVersion);
    plugin.setDependencies(List.of(mariaJdbcDriverDependency(mariaJdbcDriverVersion)));
    return plugin;
  }

  private GenerateMojo buildMojoWithMocks() {
    GenerateMojo mojo = new GenerateMojo();
    setMojoField(mojo, "project", mavenProject);
    setMojoField(mojo, "session", mavenSession);
    setMojoField(mojo, "buildPluginManager", buildPluginManager);
    setMojoField(mojo, "mariaDockerImageName", MARIA_DOCKER_IMAGE_NAME);
    setMojoField(mojo, "mariaJdbcDriverVersion", MARIA_JDBC_DRIVER_VERSION);
    setMojoField(mojo, "liquibaseMavenPluginVersion", LIQUIBASE_MAVEN_PLUGIN_VERSION);
    setMojoField(mojo, "jooqCodegenMavenPluginVersion", JOOQ_CODEGEN_MAVEN_PLUGIN_VERSION);
    return mojo;
  }

  private static void setMojoField(GenerateMojo mojo, String name, Object value) {
    try {
      Field project = GenerateMojo.class.getDeclaredField(name);
      project.setAccessible(true);
      project.set(mojo, value);
      project.setAccessible(false);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

}