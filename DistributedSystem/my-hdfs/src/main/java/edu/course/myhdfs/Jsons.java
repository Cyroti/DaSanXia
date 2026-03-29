package edu.course.myhdfs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Jsons {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Jsons() {
    }
}
