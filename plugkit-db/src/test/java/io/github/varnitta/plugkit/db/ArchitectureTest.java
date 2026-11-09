package io.github.varnitta.plugkit.db;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

    private static final String DB = "io.github.varnitta.plugkit.db";
    private static final String CORE = "io.github.varnitta.plugkit.core";
    private static final String[] PUBLIC_PACKAGES = {
            DB, DB + ".config", DB + ".exception", DB + ".metrics", DB + ".spring", DB + ".sql", DB + ".tx"};

    private static final JavaClasses MAIN = new ClassFileImporter().importPath(Path.of("target/classes"));

    @Test
    void onlySpringPackagesUseSpring() {
        noClasses().that().resideOutsideOfPackages(DB + ".spring..", DB + ".internal.spring..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("the core must work without Spring on the classpath")
                .check(MAIN);
    }

    @Test
    void onlyMetricsCodeUsesMicrometer() {
        noClasses().that().resideOutsideOfPackages(DB + ".metrics..", DB + ".internal.metrics..",
                        DB + ".internal.spring..")
                .should().dependOnClassesThat().resideInAPackage("io.micrometer..")
                .because("the core must work without Micrometer on the classpath")
                .check(MAIN);
    }

    @Test
    void onlyThePostgresDialectUsesTheDriver() {
        noClasses().that().resideOutsideOfPackage(DB + ".internal.dialect.postgres..")
                .should().dependOnClassesThat().resideInAPackage("org.postgresql..")
                .because("database-specific code belongs in its dialect")
                .check(MAIN);
    }

    @Test
    void onlyTheMySqlDialectCouldUseTheMySqlDriver() {
        noClasses().that().resideOutsideOfPackage(DB + ".internal.dialect.mysql..")
                .should().dependOnClassesThat().resideInAPackage("com.mysql..")
                .because("database-specific code belongs in its dialect")
                .check(MAIN);
    }

    @Test
    void onlyPoolAndMetricsCodeUseHikari() {
        noClasses().that().resideOutsideOfPackages(DB + ".internal.pool..", DB + ".internal.metrics..")
                .should().dependOnClassesThat().resideInAPackage("com.zaxxer.hikari..")
                .because("Hikari is an implementation detail that could be replaced")
                .check(MAIN);
    }

    @Test
    void exceptionsDependOnlyOnCoreExceptions() {
        classes().that().resideInAPackage(DB + ".exception..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(DB + ".exception..", CORE + ".exception..",
                        "java..", "org.jspecify..")
                .check(MAIN);
    }

    @Test
    void configDependsOnlyOnValidation() {
        noClasses().that().resideInAPackage(DB + ".config..")
                .should().dependOnClassesThat().resideInAnyPackage(DB + ".internal.pool..", DB + ".internal.tx..",
                        DB + ".internal.statement..", DB + ".internal.dialect..", DB + ".internal.spring..",
                        DB + ".internal.metrics..", DB + ".internal.connection..")
                .check(MAIN);
    }

    @Test
    void internalPackagesHaveNoCycles() {
        slices().matching(DB + ".internal.(*)..").should().beFreeOfCycles().check(MAIN);
    }

    @Test
    void publicApiDoesNotExposeInternalTypes() {
        methods().that().arePublic()
                .and().areDeclaredInClassesThat().resideInAnyPackage(PUBLIC_PACKAGES)
                .and().areDeclaredInClassesThat().arePublic()
                .and(notGeneratedByLombok())
                .should(notMentionInternalTypes())
                .check(MAIN);
    }

    private static DescribedPredicate<JavaMethod> notGeneratedByLombok() {
        return DescribedPredicate.describe("are not Lombok builder classes",
                method -> !method.getOwner().getSimpleName().endsWith("Builder"));
    }

    private static ArchCondition<JavaMethod> notMentionInternalTypes() {
        return new ArchCondition<>("not use internal types in their signature") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Stream.concat(Stream.of(method.getRawReturnType()), method.getRawParameterTypes().stream())
                        .filter(type -> type.getPackageName().startsWith(DB + ".internal")
                                || type.getPackageName().startsWith(CORE + ".internal"))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " exposes internal type " + type.getName())));
            }
        };
    }

    @Test
    void internalImplementationClassesAreNotSubclassable() {
        classes().that().resideInAPackage(DB + ".internal..")
                .and().areNotInterfaces().and().areNotEnums().and().areNotRecords()
                .and().areNotAnonymousClasses()
                .and(notASpringConfiguration())
                .should().haveModifier(JavaModifier.FINAL)
                .check(MAIN);
    }

    private static DescribedPredicate<JavaClass> notASpringConfiguration() {
        return DescribedPredicate.describe("are not Spring configuration classes",
                type -> !type.getPackageName().endsWith(".internal.spring")
                        || !(type.getSimpleName().endsWith("AutoConfiguration")
                        || type.getSimpleName().endsWith("Configuration")
                        || type.getSimpleName().endsWith("Registrar")));
    }
}
