package com.ruoyi.generator.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ruoyi.generator.config.GenConfig;
import com.ruoyi.generator.domain.GenSysMenu;
import com.ruoyi.generator.mapper.GenSysMenuMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.GenConstants;
import com.ruoyi.common.core.text.CharsetKit;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.generator.domain.GenTable;
import com.ruoyi.generator.domain.GenTableColumn;
import com.ruoyi.generator.mapper.GenTableColumnMapper;
import com.ruoyi.generator.mapper.GenTableMapper;
import com.ruoyi.generator.util.GenUtils;
import com.ruoyi.generator.util.VelocityInitializer;
import com.ruoyi.generator.util.VelocityUtils;
import javax.sql.DataSource;
/**
 * 业务 服务层实现
 *
 * @author ruoyi
 */
@Service
public class GenTableServiceImpl implements IGenTableService
{
    private static final Logger log = LoggerFactory.getLogger(GenTableServiceImpl.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Autowired
    private GenSysMenuMapper sysMenuMapper;

    /**
     * 查询业务信息
     *
     * @param id 业务ID
     * @return 业务信息
     */
    @Override
    public GenTable selectGenTableById(Long id)
    {
        GenTable genTable = genTableMapper.selectGenTableById(id);
        setTableFromOptions(genTable);
        return genTable;
    }

    /**
     * 查询业务列表
     *
     * @param genTable 业务信息
     * @return 业务集合
     */
    @Override
    public List<GenTable> selectGenTableList(GenTable genTable)
    {
        return genTableMapper.selectGenTableList(genTable);
    }

    /**
     * 查询据库列表
     *
     * @param genTable 业务信息
     * @return 数据库表集合
     */
    @Override
    public List<GenTable> selectDbTableList(GenTable genTable)
    {
        return genTableMapper.selectDbTableList(genTable);
    }

    /**
     * 查询据库列表
     *
     * @param tableNames 表名称组
     * @return 数据库表集合
     */
    @Override
    public List<GenTable> selectDbTableListByNames(String[] tableNames)
    {
        return genTableMapper.selectDbTableListByNames(tableNames);
    }

    /**
     * 查询所有表信息
     *
     * @return 表信息集合
     */
    @Override
    public List<GenTable> selectGenTableAll()
    {
        return genTableMapper.selectGenTableAll();
    }

    /**
     * 修改业务
     *
     * @param genTable 业务信息
     * @return 结果
     */
    @Override
    @Transactional
    public void updateGenTable(GenTable genTable)
    {
        String options = JSON.toJSONString(genTable.getParams());
        genTable.setOptions(options);
        int row = genTableMapper.updateGenTable(genTable);
        if (row > 0)
        {
            for (GenTableColumn cenTableColumn : genTable.getColumns())
            {
                genTableColumnMapper.updateGenTableColumn(cenTableColumn);
            }
        }
    }

    /**
     * 删除业务对象
     *
     * @param tableIds 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional
    public void deleteGenTableByIds(Long[] tableIds)
    {
        genTableMapper.deleteGenTableByIds(tableIds);
        genTableColumnMapper.deleteGenTableColumnByIds(tableIds);
    }

    /**
     * 导入表结构
     *
     * @param tableList 导入表列表
     */
    @Override
    @Transactional
    public void importGenTable(List<GenTable> tableList)
    {
        String operName = SecurityUtils.getUsername();
        try
        {
            for (GenTable table : tableList)
            {
                String tableName = table.getTableName();
                GenUtils.initTable(table, operName);
                int row = genTableMapper.insertGenTable(table);
                if (row > 0)
                {
                    // 保存列信息
                    List<GenTableColumn> genTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
                    for (GenTableColumn column : genTableColumns)
                    {
                        GenUtils.initColumnField(column, table);
                        genTableColumnMapper.insertGenTableColumn(column);
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("导入失败：" + e.getMessage());
        }
    }

    /**
     * 预览代码
     *
     * @param tableId 表编号
     * @return 预览数据列表
     */
    @Override
    public Map<String, String> previewCode(Long tableId)
    {
        Map<String, String> dataMap = new LinkedHashMap<>();
        // 查询表信息
        GenTable table = genTableMapper.selectGenTableById(tableId);
        // 设置主子表信息
        setSubTable(table);
        // 设置主键列信息
        setPkColumn(table);
        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        for (String template : templates)
        {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, Constants.UTF8);
            tpl.merge(context, sw);
            dataMap.put(template, sw.toString());
        }
        return dataMap;
    }

    /**
     * 生成代码（下载方式）
     *
     * @param tableName 表名称
     * @return 数据
     */
    @Override
    public byte[] downloadCode(String tableName)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        generatorCode(tableName, zip);
        IOUtils.closeQuietly(zip);
        return outputStream.toByteArray();
    }

    /**
     * 生成代码（自定义路径）
     *
     * @param tableName 表名称
     */
    @Override
    public void generatorCode(String tableName)
    {
        // 查询表信息
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        // 设置主子表信息
        setSubTable(table);
        // 设置主键列信息
        setPkColumn(table);

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        boolean isGeneratorALL = true;        //是否输出所有文件，包含前端，后端
        for (String template : templates)
        {
            if (isGeneratorALL||!StringUtils.containsAny(template, "sql.vm", "api.js.vm", "index.vue.vm", "index-tree.vue.vm"))
            {
                // 渲染模板
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(template, Constants.UTF8);
                tpl.merge(context, sw);
                try
                {
                    String path = getGenPath(table, template);
                    FileUtils.writeStringToFile(new File(path), sw.toString(), CharsetKit.UTF_8);
                }
                catch (IOException e)
                {
                    throw new ServiceException("渲染模板失败，表名：" + table.getTableName());
                }
            }
        }
    }

    /**
     * 生成代码（自定义路径）
     * ITKEY自定义方法 2022年4月5日
     * @param tableName 表名称
     * @param genPath 生成的路径
     */
    public void generatorCode(String tableName,String genPath)
    {
        // 查询表信息
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        // 设置主子表信息
        setSubTable(table);
        // 设置主键列信息
        setPkColumn(table);
        //强制设置生成目录
        if(genPath!=null&&!"".equals(genPath)){
            table.setGenType("1");
            table.setGenPath(genPath);
            //强制删除目录中之前的所有文件
            File genPathDir = new File(genPath);
            if(genPathDir.exists()){
                if(genPathDir.isDirectory()){
                    for (File f : genPathDir.listFiles()) {
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
            }else{
                //目录不存在，则创建目录
                genPathDir.mkdirs();
            }
        }

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        boolean isGeneratorALL = true;        //是否输出所有文件，包含前端，后端
        for (String template : templates)
        {
            if (isGeneratorALL||!StringUtils.containsAny(template, "sql.vm", "api.js.vm", "index.vue.vm", "index-tree.vue.vm"))
            {
                // 渲染模板
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(template, Constants.UTF8);
                tpl.merge(context, sw);
                try
                {
                    String path = getGenPath(table, template);
                    FileUtils.writeStringToFile(new File(path), sw.toString(), CharsetKit.UTF_8);
                }
                catch (IOException e)
                {
                    throw new ServiceException("渲染模板失败，表名：" + table.getTableName());
                }
            }
        }
    }
    /**
     * 生成代码（自定义路径）
     *
     * @param tableName 表名称
     */
    /*
    @Override
    public void generatorCode(String tableName)
    {
        // 查询表信息
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        // 设置主子表信息
        setSubTable(table);
        // 设置主键列信息
        setPkColumn(table);

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        for (String template : templates)
        {
            if (!StringUtils.containsAny(template, "sql.vm", "api.js.vm", "index.vue.vm", "index-tree.vue.vm"))
            {
                // 渲染模板
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(template, Constants.UTF8);
                tpl.merge(context, sw);
                try
                {
                    String path = getGenPath(table, template);
                    FileUtils.writeStringToFile(new File(path), sw.toString(), CharsetKit.UTF_8);
                }
                catch (IOException e)
                {
                    throw new ServiceException("渲染模板失败，表名：" + table.getTableName());
                }
            }
        }
    }
    */

    /**
     * 同步数据库
     *
     * @param tableName 表名称
     */
    @Override
    @Transactional
    public void synchDb(String tableName)
    {
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        List<GenTableColumn> tableColumns = table.getColumns();
        Map<String, GenTableColumn> tableColumnMap = tableColumns.stream().collect(Collectors.toMap(GenTableColumn::getColumnName, Function.identity()));

        List<GenTableColumn> dbTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
        if (StringUtils.isEmpty(dbTableColumns))
        {
            throw new ServiceException("同步数据失败，原表结构不存在");
        }
        List<String> dbTableColumnNames = dbTableColumns.stream().map(GenTableColumn::getColumnName).collect(Collectors.toList());

        dbTableColumns.forEach(column -> {
            GenUtils.initColumnField(column, table);
            if (tableColumnMap.containsKey(column.getColumnName()))
            {
                GenTableColumn prevColumn = tableColumnMap.get(column.getColumnName());
                column.setColumnId(prevColumn.getColumnId());
                if (column.isList())
                {
                    // 如果是列表，继续保留查询方式/字典类型选项
                    column.setDictType(prevColumn.getDictType());
                    column.setQueryType(prevColumn.getQueryType());
                }
                if (StringUtils.isNotEmpty(prevColumn.getIsRequired()) && !column.isPk()
                        && (column.isInsert() || column.isEdit())
                        && ((column.isUsableColumn()) || (!column.isSuperColumn())))
                {
                    // 如果是(新增/修改&非主键/非忽略及父属性)，继续保留必填/显示类型选项
                    column.setIsRequired(prevColumn.getIsRequired());
                    column.setHtmlType(prevColumn.getHtmlType());
                }
                genTableColumnMapper.updateGenTableColumn(column);
            }
            else
            {
                genTableColumnMapper.insertGenTableColumn(column);
            }
        });

        List<GenTableColumn> delColumns = tableColumns.stream().filter(column -> !dbTableColumnNames.contains(column.getColumnName())).collect(Collectors.toList());
        if (StringUtils.isNotEmpty(delColumns))
        {
            genTableColumnMapper.deleteGenTableColumns(delColumns);
        }
    }

    /**
     * 批量生成代码（下载方式）
     *
     * @param tableNames 表数组
     * @return 数据
     */
    @Override
    public byte[] downloadCode(String[] tableNames)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        for (String tableName : tableNames)
        {
            generatorCode(tableName, zip);
        }
        IOUtils.closeQuietly(zip);
        return outputStream.toByteArray();
    }

    /**
     * 查询表信息并生成代码
     */
    private void generatorCode(String tableName, ZipOutputStream zip)
    {
        // 查询表信息
        GenTable table = genTableMapper.selectGenTableByName(tableName);
        // 设置主子表信息
        setSubTable(table);
        // 设置主键列信息
        setPkColumn(table);

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table.getTplCategory());
        for (String template : templates)
        {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, Constants.UTF8);
            tpl.merge(context, sw);
            try
            {
                // 添加到zip
                zip.putNextEntry(new ZipEntry(VelocityUtils.getFileName(template, table)));
                IOUtils.write(sw.toString(), zip, Constants.UTF8);
                IOUtils.closeQuietly(sw);
                zip.flush();
                zip.closeEntry();
            }
            catch (IOException e)
            {
                log.error("渲染模板失败，表名：" + table.getTableName(), e);
            }
        }
    }
    /**
     * 生成代码+一键部署
     * 1自动复制后台代码
     * 2自动复制前台代码
     * 3自动执行菜单创建sql脚本
     * 4自动重启tomcat服务
     * @param tableName 表名称
     * @return 数据
     */
    public void oneKeyDeploy(String tableName){
        //生成代码的目录
        String generatorCodePath = GenConfig.getGeneratorCodePath();
        //Java后台的Src目录
        String javaSrc = GenConfig.getJavaSrc();
        //Vue前端的Src目录
        String vueSrc = GenConfig.getVueSrc();
        generatorCode(tableName,generatorCodePath);
        //1自动复制后台代码
        //被复制的文件夹
        File srcFile = new File(generatorCodePath+File.separator+"main");
        //复制到的目录
        File destFile = new File(javaSrc+File.separator+"main");
        try {
            FileUtils.copyDirectory(srcFile,destFile);
            System.out.println("------后台代码复制成功--------");
            System.out.println("------被复制的文件夹--------"+srcFile.getPath());
            System.out.println("------复制到的目录--------"+destFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //2自动复制前台代码
        //被复制的文件夹
        File uiSrcFile = new File(generatorCodePath+File.separator+"vue");
        //复制到的目录
        File uiDestFile = new File(vueSrc);
        try {
            FileUtils.copyDirectory(uiSrcFile,uiDestFile);
            System.out.println("------UI代码复制成功--------");
            System.out.println("------被复制的文件夹--------"+uiSrcFile.getPath());
            System.out.println("------复制到的目录--------"+uiDestFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //3自动执行菜单创建sql脚本
        String sqlFile="";
        File dir = new File(generatorCodePath);
        if(dir.exists()){
            if(dir.isDirectory()){
                for (File f:dir.listFiles()) {
                    //只处理.sql结尾的文件
                    if(f.getPath().endsWith(".sql")){
                        sqlFile = f.getPath();
                        break;  //目前的场景只处理一个sql文件就好了，理论上只会有一个sql
                    }
                }
            }
        }
        //判断一下sql是否执行过，防止重复执行sql
        //读取sql文件的第3行，并用,拆分字符串
        if(sqlFile!=null&&!"".equals(sqlFile)){
            //读取sql文本
            String sqlText = readFileText(sqlFile);
            //取第一条sql的values
            String keyText = sqlText.substring(sqlText.indexOf("values("),sqlText.indexOf("');"));
            String[] keyArray = keyText.split(",");
            if(keyArray.length>=5){
                System.out.println("==============================");
                String keyword = keyArray[4];
                if(keyword.length()>=3){
                    //去掉两边的''
                    keyword = keyword.substring(2,keyword.length()-1);
                }
                System.out.println("component:"+keyword);
                System.out.println("==============================");
                //根据component字段查询，如果值已经存在则跳过执行sql
                GenSysMenu genSysMenu = new GenSysMenu();
                genSysMenu.setComponent(keyword);
                List<GenSysMenu> resultList= sysMenuMapper.selectMenuList(genSysMenu);
                if(resultList!=null&&resultList.size()>0){
                    //已经有相关的菜单了，不在重复创建了
                    log.info("已经有相关的菜单了，不在重复创建了");
                }else{
                    log.debug("正在执行创建菜单的sql");
                    String[] sqlFilePath = {sqlFile};
                    System.out.println("-------------------------sql file:---------------");
                    System.out.println(sqlFilePath[0]);
                    doExecuteSql(sqlFilePath);
                }
            }
        }
        //4自动重启tomcat服务
    }
    /**
     * 卸载一键部署的内容
     * 1自动删除后台代码
     * 2自动删除前台代码
     * 3自动删除之前菜单创建sql脚本创建的菜单数据
     * 4自动重启tomcat服务
     * @param tableName 表名称
     * @return 数据
     */
    public void oneKeyRemove(String tableName){
        //生成代码的目录
        String generatorCodePath = GenConfig.getGeneratorCodePath();
        //Java后台的Src目录
        String javaSrc = GenConfig.getJavaSrc();
        //Vue前端的Src目录
        String vueSrc = GenConfig.getVueSrc();
        //生成当前模块的代码，用于后续的比较
        generatorCode(tableName,generatorCodePath);
        //1自动复制后台代码
        //被复制的文件夹
        File srcFile = new File(generatorCodePath+File.separator+"main");
        //复制到的目录
        File destFile = new File(javaSrc+File.separator+"main");

        //后端代码的文件列表
        List<String> javaSrcFileList = new ArrayList<>();
        try {
            //FileUtils.copyDirectory(srcFile,destFile);
            System.out.println("------后台代码卸载成功--------");
            System.out.println("------java源码对照的文件夹--------"+srcFile.getPath());
            System.out.println("------卸载的项目路径--------"+destFile.getPath());
            //卸载后台代码
            //输出所有要删除的文件
            scanFile2List(srcFile,javaSrcFileList);
            System.out.println("-----------后端删除文件列表----------");
            for (String f :
                    javaSrcFileList) {
                //相对路径
                String xdlj = f.substring(generatorCodePath.length(),f.length());
                //拼出项目中源码的绝对路径

                //拼出项目中源码的绝对路径
                String realJavaFileStr =javaSrc+xdlj;
                System.out.println(realJavaFileStr);
                File realJavaFile = new File(realJavaFileStr);
                realJavaFile.delete();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        //2自动复制前台代码
        //被复制的文件夹
        File uiSrcFile = new File(generatorCodePath+File.separator+"vue");
        //复制到的目录
        File uiDestFile = new File(vueSrc);
        //后端代码的文件列表
        List<String> vueSrcFileList = new ArrayList<>();
        try {
            System.out.println("------UI代码复制成功--------");
            System.out.println("------vue源码对照的文件夹--------"+uiSrcFile.getPath());
            System.out.println("------卸载的项目路径--------"+uiDestFile.getPath());
            //卸载后台代码
            //输出所有要删除的文件
            scanFile2List(uiSrcFile,vueSrcFileList);
            System.out.println("-----------前端删除文件列表----------");
            for (String f :
                    vueSrcFileList) {
                //相对路径
                String xdlj = f.substring(generatorCodePath.length()+4,f.length());
                //拼出项目中源码的绝对路径
                String realVueFileStr =vueSrc+xdlj;
                System.out.println(realVueFileStr);
                File realVueFile = new File(realVueFileStr);
                realVueFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //3自动执行菜单创建sql脚本
        String sqlFile="";
        File dir = new File(generatorCodePath);
        if(dir.exists()){
            if(dir.isDirectory()){
                for (File f:dir.listFiles()) {
                    //只处理.sql结尾的文件
                    if(f.getPath().endsWith(".sql")){
                        sqlFile = f.getPath();
                        break;  //目前的场景只处理一个sql文件就好了，理论上只会有一个sql
                    }
                }
            }
        }
        //判断一下sql是否执行过，防止重复执行sql
        //读取sql文件的第3行，并用,拆分字符串
        if(sqlFile!=null&&!"".equals(sqlFile)){
            //读取sql文本
            String sqlText = readFileText(sqlFile);
            //取第一条sql的values
            String keyText = sqlText.substring(sqlText.indexOf("values("),sqlText.indexOf("');"));
            String[] keyArray = keyText.split(",");
            if(keyArray.length>=5){
                System.out.println("==============================");
                String keyword = keyArray[4];
                if(keyword.length()>=3){
                    //去掉两边的''
                    keyword = keyword.substring(2,keyword.length()-1);
                }
                System.out.println("component:"+keyword);
                System.out.println("==============================");
                //根据component字段查询，如果值已经存在则跳过执行sql
                GenSysMenu genSysMenu = new GenSysMenu();
                genSysMenu.setComponent(keyword);
                List<GenSysMenu> resultList= sysMenuMapper.selectMenuList(genSysMenu);
                if(resultList!=null&&resultList.size()>0){
                    //已经有相关的菜单了，准备卸载
                    System.out.println(resultList);
                    //获取所有子菜单的列表
                    for (GenSysMenu sm :
                            resultList) {
                        genSysMenu = new GenSysMenu();
                        genSysMenu.setParentId(sm.getMenuId());
                        List<GenSysMenu> sonList= sysMenuMapper.selectMenuList(genSysMenu);
                        System.out.println(sonList);
                        //删除子菜单
                        for (GenSysMenu son :
                                sonList) {
                            System.out.println("-------删除子菜单-----MenuName:"+son.getMenuName()+"---MenuId:"+son.getMenuId());
                            sysMenuMapper.deleteMenuById(son.getMenuId());
                        }

                        System.out.println("-------删除父菜单-----MenuName:"+sm.getMenuName()+"---MenuId:"+sm.getMenuId());
                        sysMenuMapper.deleteMenuById(sm.getMenuId());
                    }
                }
            }
        }
        //4自动重启tomcat服务
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

    /**
     * 修改保存参数校验
     *
     * @param genTable 业务信息
     */
    @Override
    public void validateEdit(GenTable genTable)
    {
        if (GenConstants.TPL_TREE.equals(genTable.getTplCategory()))
        {
            String options = JSON.toJSONString(genTable.getParams());
            JSONObject paramsObj = JSONObject.parseObject(options);
            if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_CODE)))
            {
                throw new ServiceException("树编码字段不能为空");
            }
            else if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_PARENT_CODE)))
            {
                throw new ServiceException("树父编码字段不能为空");
            }
            else if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_NAME)))
            {
                throw new ServiceException("树名称字段不能为空");
            }
            else if (GenConstants.TPL_SUB.equals(genTable.getTplCategory()))
            {
                if (StringUtils.isEmpty(genTable.getSubTableName()))
                {
                    throw new ServiceException("关联子表的表名不能为空");
                }
                else if (StringUtils.isEmpty(genTable.getSubTableFkName()))
                {
                    throw new ServiceException("子表关联的外键名不能为空");
                }
            }
        }
    }

    /**
     * 设置主键列信息
     *
     * @param table 业务表信息
     */
    public void setPkColumn(GenTable table)
    {
        for (GenTableColumn column : table.getColumns())
        {
            if (column.isPk())
            {
                table.setPkColumn(column);
                break;
            }
        }
        if (StringUtils.isNull(table.getPkColumn()))
        {
            table.setPkColumn(table.getColumns().get(0));
        }
        if (GenConstants.TPL_SUB.equals(table.getTplCategory()))
        {
            for (GenTableColumn column : table.getSubTable().getColumns())
            {
                if (column.isPk())
                {
                    table.getSubTable().setPkColumn(column);
                    break;
                }
            }
            if (StringUtils.isNull(table.getSubTable().getPkColumn()))
            {
                table.getSubTable().setPkColumn(table.getSubTable().getColumns().get(0));
            }
        }
    }

    /**
     * 设置主子表信息
     *
     * @param table 业务表信息
     */
    public void setSubTable(GenTable table)
    {
        String subTableName = table.getSubTableName();
        if (StringUtils.isNotEmpty(subTableName))
        {
            table.setSubTable(genTableMapper.selectGenTableByName(subTableName));
        }
    }

    /**
     * 设置代码生成其他选项值
     *
     * @param genTable 设置后的生成对象
     */
    public void setTableFromOptions(GenTable genTable)
    {
        JSONObject paramsObj = JSONObject.parseObject(genTable.getOptions());
        if (StringUtils.isNotNull(paramsObj))
        {
            String treeCode = paramsObj.getString(GenConstants.TREE_CODE);
            String treeParentCode = paramsObj.getString(GenConstants.TREE_PARENT_CODE);
            String treeName = paramsObj.getString(GenConstants.TREE_NAME);
            String parentMenuId = paramsObj.getString(GenConstants.PARENT_MENU_ID);
            String parentMenuName = paramsObj.getString(GenConstants.PARENT_MENU_NAME);

            genTable.setTreeCode(treeCode);
            genTable.setTreeParentCode(treeParentCode);
            genTable.setTreeName(treeName);
            genTable.setParentMenuId(parentMenuId);
            genTable.setParentMenuName(parentMenuName);
        }
    }

    /**
     * 获取代码生成地址
     *
     * @param table 业务表信息
     * @param template 模板文件路径
     * @return 生成地址
     */
    public static String getGenPath(GenTable table, String template)
    {
        String genPath = table.getGenPath();
        if (StringUtils.equals(genPath, "/"))
        {
            return System.getProperty("user.dir") + File.separator + "src" + File.separator + VelocityUtils.getFileName(template, table);
        }
        return genPath + File.separator + VelocityUtils.getFileName(template, table);
    }

    /**
     * 使用ScriptRunner执行SQL脚本
     * @param  filePaths 是文件的绝对路径数组，格式如下：
     * String[] filePaths = {"/xxx/sql/update1.sql", "/xxx/sql/update2.sql"};
     */
    public void doExecuteSql(String[] filePaths) {
        //通过数据源获取数据库链接
        Connection connection = DataSourceUtils.getConnection(dataSource);
        //创建脚本执行器
        ScriptRunner scriptRunner = new ScriptRunner(connection);
        //创建字符输出流，用于记录SQL执行日志
        StringWriter writer = new StringWriter();
        PrintWriter print = new PrintWriter(writer);
        //设置执行器日志输出
        scriptRunner.setLogWriter(print);
        //设置执行器错误日志输出
        scriptRunner.setErrorLogWriter(print);
        //设置读取文件格式
        Resources.setCharset(StandardCharsets.UTF_8);

        for (String path : filePaths) {
            Reader reader = null;
            try {
                File file = new File(path);
                //获取资源文件的字符输入流
                if(file.exists()) {
                    reader = new FileReader(file);
                }
            } catch (IOException e) {
                //文件流获取失败，关闭链接
                log.error(e.getMessage(), e);
                scriptRunner.closeConnection();
                return;
            }
            //执行SQL脚本
            scriptRunner.runScript(reader);
            //关闭文件输入流
            try {
                reader.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        //输出SQL执行日志
        log.debug(writer.toString());
        //关闭输入流
        scriptRunner.closeConnection();
    }
    /**
     * 读取文本内容
     * @param filePath 文件路径
     * @return
     */
    public static String readFileText(String filePath) {
        File file = new File(filePath);
        BufferedReader reader = null;
        StringBuffer sb = new StringBuffer();
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempStr;
            while ((tempStr = reader.readLine()) != null) {
                sb.append(tempStr).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return sb.toString();
    }
}
