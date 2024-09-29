package com.hibegin.http.server.config;

import com.hibegin.http.server.execption.ForbiddenException;
import com.hibegin.template.BasicTemplateRender;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LocalFileStaticResourceLoader implements StaticResourceLoader {

    private final boolean enableAutoIndex;
    private final String aliasPath;
    private final String location;


    private String buildHtmlStr(File file, String basePath) {
        StringBuilder sb = new StringBuilder();
        File[] fileArr = file.listFiles();
        if (fileArr != null) {
            List<File> files = Arrays.asList(fileArr);
            if (!Objects.equals(new File(basePath).toString(), file.toString())) {
                sb.append("<a href=\"../\">../</a>\n");
            }
            if (files.isEmpty()) {
                sb.append("<div>Empty folder (").append(file).append(")</div>");
            }
            sortFiles(files);
            for (File f : files) {
                appendFileInfo(sb, f, basePath, 48);
            }
        }
        return sb.toString();
    }

    private void appendFileInfo(StringBuilder sb, File f, String basePath, int maxLength) {
        String[] arr = fullBlankChar(f.getName(), maxLength);
        if (f.isDirectory()) {
            sb.append("<a href=\"").append(basePath).append(f.getName()).append("/\">").
                    append(arr[0]).append("/</a>").append(arr[1].subSequence(0, arr[1].length() - 1)).
                    append(formatDateTime(f.lastModified()))
                    .append("                   -\n");
        } else {
            sb.append("<a href=\"").append(basePath).append(f.getName()).append("?preview=true\">")
                    .append(arr[0]).append("</a>").append(arr[1]).
                    append(formatDateTime(f.lastModified()))
                    .append("                   ").append(f.length()).append("\n");
        }
    }

    private static String[] fullBlankChar(String str, int maxLength) {
        String newStr = str;
        String[] newStrArr = new String[2];
        String blank = "";
        if (str.length() > maxLength) {
            newStr = newStr.substring(0, maxLength - 3) + "...";
        } else {
            blank = " ".repeat(maxLength - str.length());
        }
        newStrArr[0] = newStr;
        newStrArr[1] = blank + "   ";
        return newStrArr;
    }

    private static String formatDateTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(time));
    }


    private static void sortFiles(List<File> files) {
        // 将隐藏文件放在列表前面
        files.sort((f1, f2) -> {
            if (f1.isHidden() && !f2.isHidden()) {
                return -1;
            } else if (!f1.isHidden() && f2.isHidden()) {
                return 1;
            } else {
                return f1.getName().compareTo(f2.getName());
            }
        });
    }

    private String changeFileSplitUriPath(String file) {
        return file.replace("\\", "/");
    }

    public LocalFileStaticResourceLoader(String location, String aliasPath) {
        this.enableAutoIndex = false;
        this.aliasPath = aliasPath;
        this.location = location;
    }

    public LocalFileStaticResourceLoader(boolean enableAutoIndex, String location, String aliasPath) {
        this.enableAutoIndex = enableAutoIndex;
        this.aliasPath = new File(changeFileSplitUriPath(aliasPath)).toString();
        this.location = location;
    }

    @Override
    public InputStream getInputStream(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        if (file.isDirectory()) {
            if (!enableAutoIndex) {
                throw new ForbiddenException(path);
            }
            String fileFolder = file.toString().substring(aliasPath.length());
            //System.out.println("uri = " + fileFolder);
            Map<String, Object> map = new HashMap<>();
            map.put("startPath", path);
            map.put("fileHtmlStr", buildHtmlStr(file, changeFileSplitUriPath(location + fileFolder + "/").replace("//", "/")));
            return new ByteArrayInputStream(new BasicTemplateRender(map, LocalFileStaticResourceLoader.class).renderByTemplateName("/template/sf/index.html").getBytes());
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
