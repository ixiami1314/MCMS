/**
 * Copyright (c) 2012-present 铭软科技(mingsoft.net)
 * 本软件及相关文档文件（以下简称“软件”）的版权归 铭软科技 所有
 * 遵循 铭软科技《服务协议》中的《保密条款》
 */






package net.mingsoft.basic.aop;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import net.mingsoft.base.entity.ResultData;
import net.mingsoft.basic.action.BaseFileAction;
import net.mingsoft.basic.util.BasicUtil;
import net.mingsoft.config.MSProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 验证zip是否合法的aop
 * 重写basic增加自定义配置
 */
@Component
@Aspect
@Slf4j
public class FileVerifyAop extends BaseAop{

    private static final Logger LOGGER = LoggerFactory.getLogger(FileVerifyAop.class);


    /**
     * 切入点
     */
    @Pointcut("execution(* net.mingsoft.basic.action.ManageFileAction.upload(..)) || " +
              "execution(* net.mingsoft.basic.action.ManageFileAction.uploadTemplate(..))")
    public void uploadPointCut(){}


    /**
     * 后台上传文件的时候，将验证zip里的文件
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("uploadPointCut()")
    public Object uploadAop(ProceedingJoinPoint joinPoint) throws Throwable {
        BaseFileAction.Bean bean = super.getType(joinPoint, BaseFileAction.Bean.class);
        //检查文件类型
        LOG.debug("当前上传文件信息 {}", JSONObject.toJSONString(bean.getFile()));
        String uploadFileName = FileNameUtil.cleanInvalid(bean.getFile().getOriginalFilename());
        if (StringUtils.isBlank(uploadFileName)) {
            return ResultData.build().error("文件名不能为空!");
        }
        InputStream inputStream = bean.getFile().getInputStream();

        //文件的真实类型
        String mimeType = BasicUtil.getMimeType(inputStream,uploadFileName);

        //校验压缩包里的文件
        if ("zip".equalsIgnoreCase(mimeType)){
            try {
                checkZip(bean.getFile(),false);
            } catch (Exception e) {
                return ResultData.build().error(e.getMessage());
            }
        }
        return joinPoint.proceed();
    }

    /**
     * web上传文件的时候，将验证zip里的文件
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("execution(* net.mingsoft.basic.action.web.FileAction.upload(..))")
    public Object WebUploadAop(ProceedingJoinPoint joinPoint) throws Throwable {
        BaseFileAction.Bean bean = super.getType(joinPoint, BaseFileAction.Bean.class);
        //检查文件类型
        String uploadFileName = FileNameUtil.cleanInvalid(bean.getFile().getOriginalFilename());
        if (StringUtils.isBlank(uploadFileName)) {
            return ResultData.build().error("文件名不能为空!");
        }
        InputStream inputStream = bean.getFile().getInputStream();

        //文件的真实类型
        String mimeType = BasicUtil.getMimeType(inputStream,uploadFileName);

        //校验压缩包里的文件
        if ("zip".equalsIgnoreCase(mimeType)){
            try {
                checkZip(bean.getFile(),true);
            } catch (Exception e) {
                return ResultData.build().error(e.getMessage());
            }
        }
        return joinPoint.proceed();
    }


    /**
     * 检查压缩包
     */
    private void checkZip(MultipartFile multipartFile, boolean isWeb) throws Exception{
        //创建临时解压文件夹
        File tempFilePath = FileUtil.mkdir(FileUtil.getTmpDirPath()+"/Zip"+IdUtil.simpleUUID());
        File zipFile = FileUtil.file(tempFilePath.getAbsolutePath() + "/" + IdUtil.simpleUUID() +".zip");
        multipartFile.transferTo(zipFile);

        try {
            unzip(zipFile,tempFilePath.getAbsolutePath());
            //获取文件夹下所有文件
            List<File> files = FileUtil.loopFiles(tempFilePath);
            //移除压缩包自身
            files.remove(zipFile);
            //禁止上传的格式
            List<String> deniedList = Arrays.stream(MSProperties.upload.denied.split(",")).map(String::toLowerCase).collect(Collectors.toList());
            for (File file : files) {
                FileInputStream fileInputStream = new FileInputStream(file);
                //文件的真实类型
                String fileType = FileTypeUtil.getType(file).toLowerCase();
                //通过yml的配置检查文件格式是否合法
                if (deniedList.contains(fileType)){
                    IOUtils.closeQuietly(fileInputStream);
                    throw new RuntimeException(StrUtil.format("压缩包内文件{}的类型{}禁止上传",file.getName(),fileType));
                }
                IOUtils.closeQuietly(fileInputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            FileUtil.del(tempFilePath);
        }

    }


    /**
     * 解压压缩包
     * @param file
     * @param descDir
     * @throws IOException
     */
    private  void unzip(File file, String descDir) throws IOException {
        ZipArchiveInputStream inputStream = new ZipArchiveInputStream(new BufferedInputStream(new FileInputStream(file)));
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }
        ZipArchiveEntry entry = null;
        while ((entry = inputStream.getNextZipEntry()) != null) {
            String[] dirs = entry.getName().split("/");
            String tempDir = descDir;
            for(String dir:dirs) {
                if(dir.indexOf(".")==-1) {
                    tempDir += File.separator.concat(dir);
                    FileUtil.mkdir(tempDir);
                }
            }
            if (entry.isDirectory()) {
                File directory = new File(descDir, entry.getName());
                directory.mkdirs();
            } else {
                OutputStream os = null;
                try {
                    LOGGER.debug("file name => {}",entry.getName());
                    try {
                        os = new BufferedOutputStream(new FileOutputStream(new File(descDir, entry.getName())));
                        //输出文件路径信息
                        IOUtils.copy(inputStream, os);
                    } catch (FileNotFoundException e) {
                        LOGGER.error("解压{}不存在",entry.getName());
                        e.printStackTrace();

                    }
                } finally {
                    IOUtils.closeQuietly(os);
                }
            }
        }
        IOUtils.closeQuietly(inputStream);
    }
}
