/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zxingbench.corpus;

/**
 * One sample's outcome. Values are disjoint and counted separately.
 *
 * <p>Shared by both L1 runners. A status that means one thing on the host and another on
 * a device would make the two result files look comparable while they are not.
 */
public enum L1Status {
  /** Decoded, and both format and text matched the manifest. */
  CORRECT,
  /** Decoded something, but it did not match the manifest. */
  INCORRECT,
  /** Expected a result, got none. */
  NOT_FOUND,
  /** Negative sample, correctly produced no result. */
  CORRECT_NO_RESULT,
  /** Negative sample that decoded anyway. */
  FALSE_POSITIVE,
  /** The decoder threw something that is not "nothing here". */
  ERROR
}
