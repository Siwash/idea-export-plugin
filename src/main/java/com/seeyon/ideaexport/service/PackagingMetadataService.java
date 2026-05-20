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
 * Maven 打包元数据解析服务，负责为 bug jar 模式提供模块 artifactId。
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
     * 解析单个模块的打包信息，只读取 project/artifactId 作为 bug jar 命名来源。
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
        if (artifactId.isBlank()) {
            throw new ExportException("模块缺少 artifactId，无法确定 bug jar 名: " + item.moduleName());
        }

        // bug jar 目录必须跟随当前工程 artifactId，不能使用 finalName 或 parent artifactId。
        Path classesOutputDirectory = item.moduleBasePath().resolve("target").resolve("classes");
        return new ModulePackagingInfo(item.moduleName(), artifactId, classesOutputDirectory);
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
