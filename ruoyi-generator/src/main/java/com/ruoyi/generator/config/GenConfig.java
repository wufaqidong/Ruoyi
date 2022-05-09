package com.ruoyi.generator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 读取代码生成相关配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "gen")
@PropertySource(value = { "classpath:generator.yml" })
public class GenConfig
{
    /** 作者 */
    public static String author;

    /** 生成包路径 */
    public static String packageName;

    /** 自动去除表前缀，默认是false */
    public static boolean autoRemovePre;

    /** 表前缀(类名不会包含表前缀) */
    public static String tablePrefix;

    //生成代码保存的目录(临时目录)
    public static String generatorCodePath;
    //Java后台的Src目录（后端项目代码存放的目录）
    public static String javaSrc;
    //Vue前端的Src目录（前端项目代码存放的目录）
    public static String vueSrc;

    public static String getAuthor()
    {
        return author;
    }

    @Value("${author}")
    public void setAuthor(String author)
    {
        GenConfig.author = author;
    }

    public static String getPackageName()
    {
        return packageName;
    }

    @Value("${packageName}")
    public void setPackageName(String packageName)
    {
        GenConfig.packageName = packageName;
    }

    public static boolean getAutoRemovePre()
    {
        return autoRemovePre;
    }

    @Value("${autoRemovePre}")
    public void setAutoRemovePre(boolean autoRemovePre)
    {
        GenConfig.autoRemovePre = autoRemovePre;
    }

    public static String getTablePrefix()
    {
        return tablePrefix;
    }

    @Value("${tablePrefix}")
    public void setTablePrefix(String tablePrefix)
    {
        GenConfig.tablePrefix = tablePrefix;
    }

    public static String getGeneratorCodePath() {
        return generatorCodePath;
    }
    @Value("${generatorCodePath}")
    public  void setGeneratorCodePath(String generatorCodePath) {
        GenConfig.generatorCodePath = generatorCodePath;
    }

    public static String getJavaSrc() {
        return javaSrc;
    }
    @Value("${javaSrc}")
    public void setJavaSrc(String javaSrc) {
        GenConfig.javaSrc = javaSrc;
    }

    public static String getVueSrc() {
        return vueSrc;
    }
    @Value("${vueSrc}")
    public void setVueSrc(String vueSrc) {
        GenConfig.vueSrc = vueSrc;
    }
}
