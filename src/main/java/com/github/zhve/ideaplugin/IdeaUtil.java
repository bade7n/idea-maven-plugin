package com.github.zhve.ideaplugin;

public class IdeaUtil {
    private final String basePath;

    public IdeaUtil(String path) {
        this.basePath = path;
    }

    public String relativePath(String p1) {
        return p1.replace(basePath, "$MODULE_DIR$");
    }
}
