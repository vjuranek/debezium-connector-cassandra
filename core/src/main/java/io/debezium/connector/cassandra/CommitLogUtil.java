/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.cassandra.exceptions.CassandraConnectorDataException;

/**
 * Utility class used by the CommitLogProcessor to compare/delete commit log files.
 */
public final class CommitLogUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommitLogUtil.class);

    private static final Pattern FILENAME_REGEX_PATTERN = Pattern.compile("CommitLog-\\d+-(\\d+).log");

    private static final Pattern FILENAME_INDEX_REGEX_PATTERN = Pattern.compile("CommitLog-\\d+-(\\d+)_cdc.idx");

    private CommitLogUtil() {
    }

    /**
     * Move a commit log to a new directory. If the commit log already exists in the new directory, it would be replaced.
     *
     * @throws RuntimeException exception in case moving failed
     */
    public static void moveCommitLog(Path file, Path toDir) {
        try {
            Matcher filenameMatcher = FILENAME_REGEX_PATTERN.matcher(file.getFileName().toString());
            if (!filenameMatcher.matches()) {
                LOGGER.warn("Cannot move file {} because it does not appear to be a CommitLog.", file.toAbsolutePath());
                return;
            }
            Files.move(file, toDir.resolve(file.getFileName()), REPLACE_EXISTING);
            LOGGER.info("Moved CommitLog file {} to {}.", file, toDir);
        }
        catch (Exception ex) {
            LOGGER.warn("Failed to move CommitLog file {} to {}. Error:", file.getFileName().toString(), toDir, ex);
            throw new RuntimeException(ex);
        }

        Path indexFile = file.getParent().resolve(file.getFileName().toString().split("\\.")[0] + "_cdc.idx");

        try {
            if (Files.exists(indexFile)) {
                Files.move(indexFile, toDir.resolve(indexFile.getFileName()), REPLACE_EXISTING);
                LOGGER.info("Moved CommitLog index file {} to {}.", indexFile, toDir);
            }
        }
        catch (Exception ex) {
            LOGGER.warn("Failed to move CommitLog index file {} to {}. Error:", indexFile.toAbsolutePath(), toDir, ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Delete a commit log and logs the error in the case the deletion failed.
     */
    public static void deleteCommitLog(File file) {
        try {
            Matcher filenameMatcher = FILENAME_REGEX_PATTERN.matcher(file.getName());
            if (!filenameMatcher.matches()) {
                LOGGER.warn("Cannot delete file {} because it does not appear to be a CommitLog", file.getName());
            }
            Files.delete(file.toPath());
            LOGGER.info("Deleted CommitLog file {} from {}.", file.getName(), file.getParent());
        }
        catch (Exception e) {
            LOGGER.warn("Failed to delete CommitLog file {} from {}. Error: ", file.getName(), file.getParent(), e);
            throw new RuntimeException(e);
        }

        Path indexFile = Paths.get(file.toString().split("\\.")[0] + "_cdc.idx");

        try {
            if (Files.exists(indexFile)) {
                Files.delete(indexFile);
                LOGGER.info("Deleted CommitLog index file {} from {}.", indexFile.getFileName(), indexFile.getParent());
            }
        }
        catch (Exception ex) {
            LOGGER.warn("Failed to delete CommitLog index file {}. Error:", indexFile.toAbsolutePath(), ex);
        }
    }

    /**
     * Given a directory, return an array of commit logs in this directory.
     * If the directory does not contain any commit logs, an empty array is returned.
     */
    public static File[] getCommitLogs(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Given directory does not exist: " + directory);
        }

        return directory.listFiles(f -> f.isFile() && FILENAME_REGEX_PATTERN.matcher(f.getName()).matches());
    }

    /**
     * Given a directory, return an array of commit logs' marker files in this directory.
     * If the directory does not contain any commit logs, an empty array is returned.
     */
    public static File[] getIndexes(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Given directory does not exist: " + directory);
        }
        return directory.listFiles(f -> f.isFile() && FILENAME_INDEX_REGEX_PATTERN.matcher(f.getName()).matches());
    }

    /**
     * Comparing two commit log files provided the {@link File} instances;
     * Returns 0 if they are the same, -1 if first file is older, 1 if first file is newer.
     */
    public static int compareCommitLogs(File file1, File file2) {
        if (file1.equals(file2)) {
            return 0;
        }
        long ts1 = extractTimestamp(file1.getName(), FILENAME_REGEX_PATTERN);
        long ts2 = extractTimestamp(file2.getName(), FILENAME_REGEX_PATTERN);
        return Long.compare(ts1, ts2);
    }

    public static int compareCommitLogs(String file1, String file2) {
        if (file1.equals(file2)) {
            return 0;
        }
        long ts1 = extractTimestamp(file1, FILENAME_REGEX_PATTERN);
        long ts2 = extractTimestamp(file2, FILENAME_REGEX_PATTERN);
        return Long.compare(ts1, ts2);
    }

    /**
     * Comparing two commit log files provided the {@link File} instances;
     * Returns 0 if they are the same, -1 if first file is older, 1 if first file is newer.
     */
    public static int compareCommitLogsIndexes(File file1, File file2) {
        if (file1.equals(file2)) {
            return 0;
        }
        long ts1 = extractTimestamp(file1.getName(), FILENAME_INDEX_REGEX_PATTERN);
        long ts2 = extractTimestamp(file2.getName(), FILENAME_INDEX_REGEX_PATTERN);
        return Long.compare(ts1, ts2);
    }

    private static long extractTimestamp(String commitLogFileName, Pattern pattern) {
        Matcher filenameMatcher = pattern.matcher(commitLogFileName);
        if (!filenameMatcher.matches()) {
            throw new CassandraConnectorDataException("Cannot extract timestamp because " + commitLogFileName + " does not appear to be a CommitLog");
        }
        return Long.parseLong(filenameMatcher.group(1));
    }
}
