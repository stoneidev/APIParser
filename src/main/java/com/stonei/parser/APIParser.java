package com.stonei.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class APIParser {
    private static final List<String[]> apiList = new ArrayList<>();

    public static void main(String[] args) {
        try {
            // API 정보를 저장할 리스트 초기화
            apiList.add(new String[] { 
                "Class Name",    // 클래스 이름을 첫 번째로
                "Method Name",   // 메소드 이름을 두 번째로
                "HTTP Method", 
                "Path", 
                "Description"
            });

            // 컨트롤러가 있는 디렉토리 경로 설정
            String projectPath = "/Users/tandem/Projects/Cursor/lds/src/main/java/com/stoneistudio/lds/member/framework/adapters/input";

            // 모든 Java 파일을 찾아서 멀티 쓰레드로 처리
            try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .parallel() // 멀티 쓰레드 실행
                        .forEach(APIParser::processJavaFile);
            }

            // 텍스트 파일로 저장
            try (FileWriter writer = new FileWriter("api-list.txt")) {
                for (String[] row : apiList) {
                    writer.write(String.join(",", row) + "\n");
                }
            }

            System.out.println("API 목록이 성공적으로 텍스트 파일로 저장되었습니다.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processJavaFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);

            // 클래스에서 컨트롤러 어노테이션 확인
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                if (isController(classDecl)) {
                    String baseUrl = getBaseUrl(classDecl);

                    // 각 메소드 처리
                    classDecl.getMethods().forEach(method -> processMethod(method, baseUrl));
                }
            });
        } catch (Exception e) {
            System.err.println("파일 처리 중 오류 발생: " + javaFile);
            e.printStackTrace();
        }
    }

    private static boolean isController(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("RestController") ||
                        ann.getNameAsString().equals("Controller"));
    }

    private static String getBaseUrl(ClassOrInterfaceDeclaration classDecl) {
        Optional<AnnotationExpr> requestMapping = classDecl.getAnnotations().stream()
                .filter(ann -> ann.getNameAsString().equals("RequestMapping"))
                .findFirst();

        return requestMapping.map(ann -> {
            if (ann.isSingleMemberAnnotationExpr()) {
                return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
            } else if (ann.isNormalAnnotationExpr()) {
                return ann.asNormalAnnotationExpr().getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals("value"))
                        .findFirst()
                        .map(pair -> pair.getValue().toString().replace("\"", ""))
                        .orElse("");
            }
            return "";
        }).orElse("");
    }

    private static void processMethod(MethodDeclaration method, String baseUrl) {
        String methodName = method.getNameAsString();
        String className = method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("");
        
        method.getAnnotations().stream()
                .filter(ann -> isHttpMethodAnnotation(ann.getNameAsString()))
                .forEach(ann -> {
                    String httpMethod = getHttpMethod(ann.getNameAsString());
                    
                    if (ann.getNameAsString().equals("RequestMapping")) {
                        httpMethod = getRequestMappingMethod(ann);
                    }
                    
                    String path = baseUrl + getMethodPath(ann);
                    if (path.startsWith("/")) {
                        path = path.substring(1); // Remove leading slash if exists
                    }
                    String description = method.getJavadoc()
                            .map(doc -> doc.getDescription().toText())
                            .orElse("");

                    apiList.add(new String[] { 
                        className,    // 클래스 이름을 첫 번째로
                        methodName,   // 메소드 이름을 두 번째로
                        httpMethod, 
                        "/" + path,   // Ensure path starts with a slash
                        description
                    });
                });
    }

    private static boolean isHttpMethodAnnotation(String annotationName) {
        return annotationName.equals("GetMapping") ||
                annotationName.equals("PostMapping") ||
                annotationName.equals("PutMapping") ||
                annotationName.equals("DeleteMapping") ||
                annotationName.equals("RequestMapping");
    }

    private static String getHttpMethod(String annotationName) {
        switch (annotationName) {
            case "GetMapping":
                return "GET";
            case "PostMapping":
                return "POST";
            case "PutMapping":
                return "PUT";
            case "DeleteMapping":
                return "DELETE";
            case "RequestMapping":
                return "ALL";
            default:
                return "";
        }
    }

    private static String getMethodPath(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return annotation.asSingleMemberAnnotationExpr()
                    .getMemberValue().toString().replace("\"", "");
        } else if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value"))
                    .findFirst()
                    .map(pair -> pair.getValue().toString().replace("\"", ""))
                    .orElse("");
        }
        return "";
    }

    private static String getRequestMappingMethod(AnnotationExpr annotation) {
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("method"))
                    .findFirst()
                    .map(pair -> {
                        String methodValue = pair.getValue().toString();
                        if (methodValue.contains("GET")) return "GET";
                        if (methodValue.contains("POST")) return "POST";
                        if (methodValue.contains("PUT")) return "PUT";
                        if (methodValue.contains("DELETE")) return "DELETE";
                        return "ALL";
                    })
                    .orElse("ALL");
        }
        return "ALL";
    }
}
