package com.ruoyi.generator.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Test {
/*    public static void main(String[] args) {
        File dir = new File("/Users/itkey/Downloads/generatorCode");
        if(dir.exists()){
            if(dir.isDirectory()){
                for (File f:dir.listFiles()) {
                   if(f.isDirectory()){
                       try {
                           FileUtils.deleteDirectory(f);
                       } catch (IOException exception) {
                           exception.printStackTrace();
                       }
                   }else{
                       f.delete();
                   }
                }

            }
        }
    }*/
    public static void main(String[] args) throws Exception {
        File file = new File("/Users/itkey/Downloads/generatorCode") ; // 文件
        List<String> fileList = new ArrayList<String>();
        scanFile2List(file,fileList) ; // 设置要列表的目录路径
        System.out.println(fileList);
    }

    /**
     * 扫描目录下
     * @param file 目录路径
     * @param fileList 保存文件列表
     */
    public static void scanFile2List(File file, List<String> fileList) {
        if (file.isDirectory()) {   // 当前的路径是一个目录
            File list[] = file.listFiles((f) -> f.isDirectory() ? true : !f.isDirectory()); // 列出目录中的全部组成
            for (File temp : list) {
                scanFile2List(temp,fileList) ; // 递归操作，继续列出
            }
        } else {
            fileList.add(file.getPath());
        }
    }

}
