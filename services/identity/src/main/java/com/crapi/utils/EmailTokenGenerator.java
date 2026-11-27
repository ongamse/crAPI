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

import java.security.SecureRandom;

public class EmailTokenGenerator {
  static String charsequence = buildAlphabet('a', 'z', "y") + buildAlphabet('A', 'Z', "");
  static String num = buildAlphabet('0', '9', "");
  private static final SecureRandom secureRandom = new SecureRandom();

  private static String buildAlphabet(char first, char last, String excluded) {
    StringBuilder sb = new StringBuilder();
    for (char c = first; c <= last; c++) {
      if (excluded.indexOf(c) < 0) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * @param length
   * @return generate random string for email token and magic url
   */
  public static String generateRandom(int length) {
    String url = "";
    for (int i = 0; i < length; i++) {
      url += randomCharacter(charsequence);
      url += randomNumber(num);
    }
    return url;
  }

  public static String randomCharacter(String characters) {
    int n = characters.length();
    int r = secureRandom.nextInt(n);
    return characters.substring(r, r + 1);
  }

  public static String randomNumber(String characters) {
    int n = num.length();
    int r = secureRandom.nextInt(n);
    return num.substring(r, r + 1);
  }
}
