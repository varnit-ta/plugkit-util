package io.github.varnitta.plugkit.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String CORE = "io.github.varnitta.plugkit.core";

    private static final JavaClasses MAIN = new ClassFileImporter().importPath(Path.of("target/classes"));

    @Test
    void coreDependsOnlyOnTheJdkAndJSpecify() {
        classes().should().onlyDependOnClassesThat().resideInAnyPackage(CORE + "..", "java..", "org.jspecify..")
                .because("every plugkit library depends on core, so core must not pull anything in")
                .check(MAIN);
    }

    @Test
    void exceptionsDependOnlyOnTheJdk() {
        classes().that().resideInAPackage(CORE + ".exception..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(CORE + ".exception..", "java..",
                        "org.jspecify..")
                .check(MAIN);
    }

    @Test
    void internalPackagesHaveNoCycles() {
        slices().matching(CORE + ".internal.(*)..").should().beFreeOfCycles().check(MAIN);
    }

    @Test
    void publicApiDoesNotExposeInternalTypes() {
        methods().that().arePublic()
                .and().areDeclaredInClassesThat().resideInAnyPackage(CORE, CORE + ".exception")
                .should(notMentionInternalTypes())
                .check(MAIN);
    }

    @Test
    void internalClassesAreFinal() {
        classes().that().resideInAPackage(CORE + ".internal..")
                .and().areNotInterfaces().and().areNotEnums().and().areNotRecords()
                .should().haveModifier(JavaModifier.FINAL)
                .check(MAIN);
    }

    private static ArchCondition<JavaMethod> notMentionInternalTypes() {
        return new ArchCondition<>("not use internal types in their signature") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Stream.concat(Stream.of(method.getRawReturnType()), method.getRawParameterTypes().stream())
                        .filter(type -> type.getPackageName().startsWith(CORE + ".internal"))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " exposes internal type " + type.getName())));
            }
        };
    }
}
