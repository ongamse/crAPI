/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Executables this helper is permitted to launch. */
  private static final Set<String> ALLOWED_EXECUTABLES = Set.of("convertVideo");

  /**
   * Execute an allowlisted executable directly (no shell). Every element of the list after the
   * executable is passed to the program as one discrete argument, so shell metacharacters (; | &&
   * $ ` etc.) have no special meaning.
   *
   * @param commandAndArgs executable name followed by its arguments
   * @return the standard output of the process, or null if it could not be run.
   */
  public String executeCommand(List<String> commandAndArgs) throws IOException {
    if (commandAndArgs == null
        || commandAndArgs.isEmpty()
        || !ALLOWED_EXECUTABLES.contains(commandAndArgs.get(0))) {
      log.error("Refusing to execute a command that is not allowlisted");
      return null;
    }
    List<String> argv = new ArrayList<>(commandAndArgs);
    log.info("Executing command: {}", argv.get(0));
    try {
      Process p = new ProcessBuilder(argv).redirectErrorStream(false).start();
      StringBuilder output = new StringBuilder();
      try (BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = b.readLine()) != null) {
          output.append(line).append("\n");
        }
      }
      p.waitFor();
      return output.toString();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while executing command: {}", argv.get(0));
    } catch (Exception e) {
      log.error("Failed to execute command: {}", argv.get(0), e);
    }
    return null;
  }
}
