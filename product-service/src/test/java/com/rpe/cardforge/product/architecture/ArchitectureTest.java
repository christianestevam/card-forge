package com.rpe.cardforge.product.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.rpe.cardforge.product",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** O domínio não depende de Spring, JPA, AWS SDK ou Jackson. */
  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "software.amazon..",
              "io.awspring..",
              "com.fasterxml.jackson..");
}
