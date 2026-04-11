package com.seeyon.ideaexport.service;

import com.seeyon.ideaexport.exception.ExportException;
import com.seeyon.ideaexport.model.ModulePackagingInfo;
import com.seeyon.ideaexport.model.SelectedItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maven 打包元数据解析服务，负责为 bug jar 模式提供模块最终 jar 名。
 *
 * @Author by AI.Coding
 * @Date 2026-04-11
 */
public class PackagingMetadataService {

    /**
     * 解析本次导出涉及模块的打包信息。
     *
     * @param items 选中项列表
     * @return 模块打包信息映射
     * @throws ExportException pom 解析失败或缺少必要元数据
     */
    public Map<String, ModulePackagingInfo> resolvePackaging(List<SelectedItem> items) throws ExportException {
        Objects.requireNonNull(items, "items cannot be null");
        Map<String, ModulePackagingInfo> packagingInfo = new LinkedHashMap<>();
        for (SelectedItem item : items) {
            if (packagingInfo.containsKey(item.moduleName())) {
                continue;
            }
            packagingInfo.put(item.moduleName(), resolveSingleModule(item));
        }
        return Map.copyOf(packagingInfo);
    }

    /**
     * 解析单个模块的打包信息，优先使用 project/build/finalName，缺失时回退 project/artifactId。
     *
     * @param item 选中项
     * @return 模块打包信息
     * @throws ExportException pom 缺失或无法解析
     */
    private ModulePackagingInfo resolveSingleModule(SelectedItem item) throws ExportException {
        Path pomPath = item.moduleBasePath().resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            throw new ExportException("未找到模块 pom.xml，无法确定 bug jar 名: " + item.moduleName());
        }

        Document document = parsePomDocument(pomPath);
        Element projectElement = document.getDocumentElement();
        String artifactId = readDirectChildText(projectElement, "artifactId");
        String finalName = readBuildFinalName(projectElement);
        if (finalName.isBlank()) {
            finalName = artifactId;
        }
        if (finalName.isBlank()) {
            throw new ExportException("模块缺少 finalName/artifactId，无法确定 bug jar 名: " + item.moduleName());
        }

        // bug jar 目录名必须来自当前模块真实构建元数据，而不是全文首个匹配值。
        Path classesOutputDirectory = item.moduleBasePath().resolve("target").resolve("classes");
        return new ModulePackagingInfo(item.moduleName(), finalName, classesOutputDirectory);
    }

    /**
     * 解析 pom.xml 为 DOM 文档，只读取项目级节点，避免误命中 parent 或 plugin 配置。
     *
     * @param pomPath pom 文件路径
     * @return DOM 文档
     * @throws ExportException 解析失败
     */
    private Document parsePomDocument(Path pomPath) throws ExportException {
        try (Reader reader = Files.newBufferedReader(pomPath, StandardCharsets.UTF_8)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(reader));
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            throw new ExportException("解析 pom.xml 失败: " + pomPath, exception);
        }
    }

    /**
     * 读取 project/build/finalName，避免误取到插件或 profile 下的同名节点。
     *
     * @param projectElement project 根节点
     * @return finalName，缺失时返回空字符串
     */
    private String readBuildFinalName(Element projectElement) {
        NodeList buildNodes = projectElement.getElementsByTagName("build");
        if (buildNodes.getLength() == 0) {
            return "";
        }
        for (int index = 0; index < buildNodes.getLength(); index++) {
            if (!(buildNodes.item(index) instanceof Element buildElement)) {
                continue;
            }
            if (buildElement.getParentNode() != projectElement) {
                continue;
            }
            return readDirectChildText(buildElement, "finalName");
        }
        return "";
    }

    /**
     * 读取直接子节点文本，避免跨层级误取值。
     *
     * @param parent 父节点
     * @param tagName 标签名
     * @return 文本内容，缺失时返回空字符串
     */
    private String readDirectChildText(Element parent, String tagName) {
        NodeList childNodes = parent.getElementsByTagName(tagName);
        for (int index = 0; index < childNodes.getLength(); index++) {
            if (!(childNodes.item(index) instanceof Element childElement)) {
                continue;
            }
            if (childElement.getParentNode() != parent) {
                continue;
            }
            return childElement.getTextContent().trim();
        }
        return "";
    }
}
