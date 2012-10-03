/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapred;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Used to expire files in cache that hasn't been accessed for a while
 */
public class ExpireUnusedJobFiles implements Runnable {
  /** Logger. */
  private static final Log LOG =
    LogFactory.getLog(ExpireUnusedJobFiles.class);

  /** Clock. */
  private final Clock clock;
  /** The directory to clean. */
  private final Path dirToClean;
  /** The filesystem to use. */
  private final FileSystem fs;
  /** clean threshold in milliseconds. */
  private final long cleanThreshold;
  /** clean interval in milliseconds. */
  private final long cleanInterval;
  /** pattern to match for the files to be deleted */
  private final Pattern fileToCleanPattern;

  
  /**
   * Constructor.
   * @param clock The clock.
   * @param dirToClean The directory to be cleaned
   * @param fs The filesystem.
   * @param fileToCleanPattern the pattern for the filename
   * @param cleanThreshold the time to clean the dir
   * @param cleanInterval the interval to clean the dir
   */
  public ExpireUnusedJobFiles(
    Clock clock, FileSystem fs,
    Path dirToClean, Pattern fileToCleanPattern,
    long cleanThreshold, long cleanInterval) {
    this.clock = clock;
    this.fs = fs;
    this.dirToClean = dirToClean;
    this.fileToCleanPattern = fileToCleanPattern;
    this.cleanThreshold = cleanThreshold;
    this.cleanInterval = cleanInterval;

    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
      this,
      cleanInterval,
      cleanInterval,
      TimeUnit.MILLISECONDS);

    LOG.info("ExpireUnusedJobFiles created with " +
      " path = " + dirToClean +
      " cleanInterval = " + cleanInterval +
      " cleanThreshold = " + cleanThreshold);
  }

  @Override
  public void run() {
    long currentTime = clock.getTime();
    try {
      LOG.info(Thread.currentThread().getId() + ":Trying to clean " + dirToClean);
      if (!fs.exists(dirToClean)) {
        return;
      }

      RemoteIterator<LocatedFileStatus> itor;
      for( itor = fs.listLocatedStatus(dirToClean); itor.hasNext();) {
        LocatedFileStatus dirStat = itor.next();
        // Check if this is a directory matching the pattern
        if (!dirStat.isDir()) {
          continue;
        }
        Path subDirPath = dirStat.getPath();
        String dirname = subDirPath.toUri().getPath();
        Matcher m = fileToCleanPattern.matcher(dirname);
        if (m.find()) {
          if (currentTime - dirStat.getModificationTime() > cleanThreshold) {
            // recursively delete all the files/dirs
            LOG.info("Delete " + subDirPath);
            fs.delete(subDirPath, true);
          }
        }
      }
    } catch (IOException ioe) {
      LOG.error("IOException when clearing dir " + ioe.getMessage());
    }
  }
}


