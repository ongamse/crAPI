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
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashCommand {

  /** Allowlist for each whitespace-separated token: no shell metacharacters, quotes or expansion. */
  private static final Pattern SAFE_ARGUMENT = Pattern.compile("^[A-Za-z0-9_@%+=:,./-]+$");

  /**
   * Execute a command without a shell. The command line is split on whitespace into an argv array
   * and run directly, so shell operators (; | && || $ ` quotes, redirects) are never interpreted.
   * Every token must match a conservative allowlist, otherwise the command is rejected.
   *
   * @param command whitespace-separated program and arguments
   * @return the process standard output
   */
  public String executeBashCommand(String command) throws IOException {
    BufferedReader b = null;
    StringBuilder output;
    log.info("Executing command: {}", command);
    if (command == null || command.trim().isEmpty()) {
      throw new IOException("Empty command");
    }
    List<String> commands = new ArrayList<>();
    for (String token : command.trim().split("\\s+")) {
      if (!SAFE_ARGUMENT.matcher(token).matches()) {
        log.error("Rejected command argument containing disallowed characters");
        throw new IOException("Invalid command argument");
      }
      commands.add(token);
    }
    try {
      Process p = new ProcessBuilder(commands).start();

      p.waitFor();
      InputStreamReader data = new InputStreamReader(p.getInputStream());
      b = new BufferedReader(data);
      String line = "";
      output = new StringBuilder();
      while ((line = b.readLine()) != null) {
        output.append(line + "\n");
      }
      b.close();
      return (output != null ? String.valueOf(output) : "command not found");
    } catch (Exception e) {
      log.error("Failed to execute bash with command: " + command);
      e.printStackTrace();
    } finally {
      b.close();
    }
    return null;
  }
}
