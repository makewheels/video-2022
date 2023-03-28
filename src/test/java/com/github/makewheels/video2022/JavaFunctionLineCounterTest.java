package com.github.makewheels.video2022;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.Test;

import java.io.File;
import java.util.*;

/**
 * 统计最多行的函数
 */
public class JavaFunctionLineCounterTest {

    @Test
    public void run() {

        countFunctionLines("D:\\workSpace\\intellijidea\\video-2022");
//        countFunctionLines("D:\\workSpace\\xiaomi\\maps");
    }

    public void countFunctionLines(String rootDir) {
        // 存储每个 Java 文件中的函数名和行数信息
        Map<String, Map<String, Integer>> functionLines = new HashMap<>();

        // 遍历每个 Java 文件，统计每个函数的行数信息
        File root = new File(rootDir);
        for (File javaFile : FileUtil.loopFiles(root, path -> path.getAbsolutePath().endsWith(".java"))) {
            String className = javaFile.getName().replace(".java", "");
            FileReader fileReader = new FileReader(javaFile);
            String javaCode = fileReader.readString();
            JavaParser javaParser = new JavaParser();
            CompilationUnit cu = javaParser.parse(javaCode).getResult().get();
            Map<String, Integer> functionLineCount = new HashMap<>();
            new MethodVisitor().visit(cu, functionLineCount);
            functionLines.put(className, functionLineCount);
        }

        // 找出所有 Java 文件中行数前三多的函数
        Map<String, Integer> allFunctionLineCount = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : functionLines.entrySet()) {
            String className = entry.getKey();
            Map<String, Integer> functionLineCount = entry.getValue();
            for (Map.Entry<String, Integer> functionEntry : functionLineCount.entrySet()) {
                String functionName = functionEntry.getKey();
                int functionLinesCount = functionEntry.getValue();
                String fullFunctionName = className + "." + functionName;
                allFunctionLineCount.merge(fullFunctionName, functionLinesCount, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> functionLineCountList
                = new ArrayList<>(allFunctionLineCount.entrySet());
        functionLineCountList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        // 输出行数前三多的函数
        for (Map.Entry<String, Integer> entry : functionLineCountList.subList(
                0, Math.min(10, functionLineCountList.size()))) {
            String fullFunctionName = entry.getKey();
            int functionLinesCount = entry.getValue();
            int lastDotIndex = fullFunctionName.lastIndexOf(".");
            String className = fullFunctionName.substring(0, lastDotIndex);
            String functionName = fullFunctionName.substring(lastDotIndex + 1);
            System.out.printf("Class: %-20s Function: %-30s Lines: %d%n",
                    className, functionName, functionLinesCount);
        }
    }

    static class MethodVisitor extends VoidVisitorAdapter<Map<String, Integer>> {
        @Override
        public void visit(MethodDeclaration n, Map<String, Integer> arg) {
            super.visit(n, arg);
            int startLine = n.getBegin().get().line;
            int endLine = n.getEnd().get().line;
            int lines = endLine - startLine + 1;
            arg.put(n.getNameAsString(), lines);
        }
    }

}
