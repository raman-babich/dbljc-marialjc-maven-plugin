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

import org.apache.maven.shared.verifier.Verifier;
import org.junit.jupiter.api.Test;

/**
 * @author Raman Babich
 */
class GenerateMojoIT {

  @Test
  void shouldGenerate() throws Exception {
    String testPomDir = "/src/test/resources/integration-tests/should-generate";
    Verifier verifier = buildVerifier(testPomDir);
    verifier.addCliArguments("compile");

    verifier.execute();

    verifier.verifyFilePresent(
        "target/classes/com/ramanbabich/dbljc/marialjcmavenpluginit/jooq/tables/Data.class");
  }

  private Verifier buildVerifier(String pomDir) throws Exception {
    String userDir = System.getProperty("user.dir");
    Verifier verifier = new Verifier(userDir + pomDir);
    String localRepo = System.getProperty("verifier.local-repo");
    if (localRepo != null) {
      verifier.setLocalRepo(localRepo);
    }
    String pluginVersion = System.getProperty("marialjc-maven-plugin.version");
    verifier.addCliArgument("-Dmarialjc-maven-plugin.version=" + pluginVersion);
    return verifier;
  }

}
