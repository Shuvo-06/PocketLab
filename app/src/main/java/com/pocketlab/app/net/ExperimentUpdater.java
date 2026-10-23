package com.pocketlab.app.net;

import com.pocketlab.app.experiment.ExperimentDefinition;
import com.pocketlab.app.experiment.ExperimentParser;

import org.json.JSONException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Downloads the experiment file, validates it with the same parser the app uses, and only then
 * replaces the cache. A bad or unreachable server therefore never breaks the experiments.
 */
public final class ExperimentUpdater {

    public enum Status { UPDATED, UNCHANGED, NOT_CONFIGURED, FAILED }

    public static final class Result {
        public final Status status;
        public final String message;
        public final int count;
        /** True when trying again later could succeed (network trouble, server error). */
        public final boolean transientFailure;

        Result(Status status, String message, int count, boolean transientFailure) {
            this.status = status;
            this.message = message;
            this.count = count;
            this.transientFailure = transientFailure;
        }
    }

    private ExperimentUpdater() {}

    public static Result update(ExperimentCache cache, ExperimentDownloader downloader, String url) {
        if (url == null || url.trim().isEmpty()) {
            return new Result(Status.NOT_CONFIGURED, "No experiment server is configured.", 0, false);
        }
        try {
            String etag = cache.readJson() != null ? cache.etag() : null;
            ExperimentDownloader.Response r = downloader.fetch(url.trim(), etag);
            if (r.status == 304) return new Result(Status.UNCHANGED, "Experiments are up to date.", 0, false);

            List<ExperimentDefinition> defs;
            try {
                defs = ExperimentParser.parse(r.body);
            } catch (JSONException e) {
                return new Result(Status.FAILED, "The server sent an invalid experiment file: " + e.getMessage(), 0, false);
            }
            if (defs.isEmpty()) return new Result(Status.FAILED, "The server's experiment file is empty.", 0, false);
            cache.save(r.body, r.etag);
            return new Result(Status.UPDATED, defs.size() + " experiments downloaded.", defs.size(), false);
        } catch (IOException e) {
            return new Result(Status.FAILED, describe(e), 0, isTransient(e));
        }
    }

    static String describe(IOException e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) return "No internet connection.";
        if (e instanceof SocketTimeoutException) return "The server did not respond in time.";
        if (e instanceof HttpStatusException) {
            int c = ((HttpStatusException) e).getCode();
            if (c == 404) return "The experiment file was not found on the server (404).";
            if (c >= 500) return "The server had a problem (HTTP " + c + ").";
            return "The server refused the request (HTTP " + c + ").";
        }
        return e.getMessage() == null ? "Network error." : e.getMessage();
    }

    static boolean isTransient(IOException e) {
        if (e instanceof HttpStatusException) {
            int c = ((HttpStatusException) e).getCode();
            return c >= 500 || c == 408 || c == 429;
        }
        return e instanceof UnknownHostException || e instanceof ConnectException || e instanceof SocketTimeoutException;
    }
}
