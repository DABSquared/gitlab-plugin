package com.dabsquared.gitlabjenkins;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;

public abstract class GitLabRequest {

    protected enum Builder {
        INSTANCE;
        private final Gson gson;

        Builder() {
            gson = new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .registerTypeAdapter(Date.class, new GitLabRequest.DateSerializer())
                    .create();
        }

        public Gson get() {
            return gson;
        }
    }

    public abstract GitlabCommitStatus createCommitStatus(GitlabAPI api, String status, String targetUrl);

    private static class DateSerializer implements JsonDeserializer<Date> {

        private static final String[] DATE_FORMATS = new String[]{"yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"};

        public Date deserialize(JsonElement jsonElement, Type typeOF, JsonDeserializationContext context) throws JsonParseException {
            for (String format : DATE_FORMATS) {
                try {
                    return new SimpleDateFormat(format, Locale.US).parse(jsonElement.getAsString());
                } catch (ParseException e) {
                }
            }
            throw new JsonParseException("Unparseable date: \"" + jsonElement.getAsString() + "\". Supported formats: " + Arrays.toString(DATE_FORMATS));
        }
    }

}
