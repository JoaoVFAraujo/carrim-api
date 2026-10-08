package br.com.carrim.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureTests {
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.carrim");

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes()
                .that()
                .resideInAPackage("br.com.carrim.domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "br.com.carrim.domain..")
                .because("business rules must remain independent of frameworks, application and adapters")
                .check(PRODUCTION);
    }

    @Test
    void applicationDependsOnlyOnJavaDomainAndApplication() {
        classes()
                .that()
                .resideInAPackage("br.com.carrim.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "br.com.carrim.domain..", "br.com.carrim.application..")
                .because("use cases and ports must not import Spring, persistence or adapters")
                .check(PRODUCTION);
    }

    @Test
    void jpaEntitiesStayInPersistenceAdapter() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .resideInAPackage("br.com.carrim.adapter.out.persistence..")
                .check(PRODUCTION);
    }

    @Test
    void topLevelPackagesHaveNoDependencyCycles() {
        slices().matching("br.com.carrim.(*)..").should().beFreeOfCycles().check(PRODUCTION);
    }
}
