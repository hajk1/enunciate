/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
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
package com.webcohesion.enunciate.util;

import java.util.Comparator;

/**
 * @author Ryan Heaton
 */
public class DepthFirstResourcePathComparator implements Comparator<String> {

  public int compare(String resource1Path, String resource2Path) {
    String[] path1Segments = resource1Path.split("/");
    String[] path2Segments = resource2Path.split("/");
    int index = 0;
    int comparison = 0;
    while ((index < path1Segments.length || index < path2Segments.length) && comparison == 0) {
      if (index >= path1Segments.length || index >= path2Segments.length) {
        comparison = path1Segments.length - path2Segments.length;
      }
      else {
        String subpath1 = path1Segments[index];
        String subpath2 = path2Segments[index];
        comparison = subpath1.compareTo(subpath2);
        index++;
      }
    }

    return comparison;
  }
}
