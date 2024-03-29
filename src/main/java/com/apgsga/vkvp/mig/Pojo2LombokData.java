package com.apgsga.vkvp.mig;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;

public class Pojo2LombokData extends Recipe {

    @Override
    public String getDisplayName() {
        return "Pojo to Lombok Data Migration";
    }

    @Override
    public String getDescription() {
        return "This Recipe migrates Pojo Classes (Ro ) of VKVP to Lombok Data Classes, \n It removes setters and getters for attributes and add's the Lombok Annotation @Data.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                    ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                List<Annotation> allAnnotations = cd.getAllAnnotations();
                if (isSelectedClass(cd) 
                        && allAnnotations.stream().noneMatch(new AnnotationMatcher("@lombok.Data")::matches)) {
                    JavaTemplate template = JavaTemplate.builder(this::getCursor, "@Data")
                            .imports("lombok.Data")
                            .javaParser(() -> JavaParser.fromJavaVersion()
                                    .dependsOn("package lombok; public @interface Data {}")
                                    .build())
                            .build();
                    maybeAddImport("lombok.Data");
                    cd = cd.withTemplate(template,
                            cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = method.getMethodType();
                String simpleMethodName = m.getSimpleName();
                if (methodType != null &&
                        !method.isConstructor()) {

                    J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (classDeclaration == null) {
                        return m;
                    }
                    if (!isSelectedClass(classDeclaration))
                        return m;

                    if (!(simpleMethodName.startsWith("get") || simpleMethodName.startsWith("set")
                            || simpleMethodName.startsWith("isSet")))
                        return m;

                    return null;
                }

                return m;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                    ExecutionContext executionContext) {

                List<NamedVariable> variables = multiVariable.getVariables();
                if (variables.size() == 1 && variables.get(0).getSimpleName().startsWith("isSet_")) {
                    return null;
                }
                return super.visitVariableDeclarations(multiVariable, executionContext);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable,
                    ExecutionContext executionContext) {
                if (variable.getSimpleName().startsWith("isSet_")) {
                    return null;
                }
                return super.visitVariable(variable, executionContext);
            }

            private boolean isSelectedClass(J.ClassDeclaration cd) {
               String packageName =  cd.getType().getPackageName(); 
               if ((packageName.endsWith(".ro") || packageName.equals("ro")) && cd.getSimpleName().startsWith("Ro")) return true;
               return false; 

            }
                
        };

    }

}
