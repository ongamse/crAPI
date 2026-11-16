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
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Executables that may be launched through this helper. */
  private static final Set<String> ALLOWED_EXECUTABLES = Set.of("convertVideo");

  /**
   * Characters permitted in an argument. Shell metacharacters (; | & $ ` < > quotes, backslash,
   * newlines) are rejected.
   */
  private static final Pattern SAFE_ARGUMENT = Pattern.compile("[A-Za-z0-9_@%+=:,./()\\[\\] -]+");

  /**
   * Execute an allowlisted executable directly (no shell), passing each argument as a separate
   * argv element so that no shell interpretation of the input takes place.
   *
   * @param executable name of an allowlisted executable
   * @param arguments arguments for the executable
   * @return standard output of the process
   * @throws IOException if the process could not be started or read
   * @throws IllegalArgumentException if the executable is not allowlisted or an argument contains
   *     disallowed characters
   */
  public String executeCommand(String executable, List<String> arguments) throws IOException {
    if (executable == null || !ALLOWED_EXECUTABLES.contains(executable)) {
      throw new IllegalArgumentException("Executable is not allowed");
    }
    List<String> commands = new ArrayList<>();
    commands.add(executable);
    if (arguments != null) {
      for (String argument : arguments) {
        if (argument == null || !SAFE_ARGUMENT.matcher(argument).matches()) {
          throw new IllegalArgumentException("Command argument contains disallowed characters");
        }
        commands.add(argument);
      }
    }
    log.info("Executing command {}", executable);
    try {
      Process p = new ProcessBuilder(commands).start();
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
      log.error("Interrupted while executing command {}", executable);
    }
    return null;
  }
}
