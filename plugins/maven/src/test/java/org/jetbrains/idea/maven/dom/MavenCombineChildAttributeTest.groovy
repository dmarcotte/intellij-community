/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom

/**
 * @author Sergey Evdokimov
 */
class MavenCombineChildAttributeTest extends MavenDomTestCase {

  public void testCompletion() {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>

      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <executions>
              <execution>
                <goals>
                  <goal>test</goal>
                </goals>
                <phase>integration-test</phase>
                <configuration combine.children="<caret>">
                  <groups>integrationsTests</groups>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
""")

    assertCompletionVariants(myProjectPom, "override", "append")
  }

  public void testHighlighting() {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration combine.children="override">
          <annotationProcessors combine.self="append" />
        </configuration>
        <executions>
          <execution>
            <goals><goal>compile</goal></goals>
            <configuration combine.self="<error>appendd</error>">
              <compilerArgument combine.children="override" />
              <fork combine.self="<error>qqq</error>" />
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <profile>
      <id>testProfile</id>
      <build>
        <plugins>
          <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration combine.children="override">
              <annotationProcessors combine.self="append" />
            </configuration>
            <executions>
              <execution>
                <goals><goal>compile</goal></goals>
                <configuration combine.self="<error>appendd</error>">
                  <compilerArgument combine.children="override" />
                  <fork combine.self="<error>qqq</error>" />
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
""")

    checkHighlighting()
  }

}
