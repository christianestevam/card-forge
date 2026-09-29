package com.rpe.cardforge.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** ADR-004: o módulo técnico não contém tipos de domínio nem depende dos serviços. */
@AnalyzeClasses(
    packages = "com.rpe.cardforge.platform",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PlatformArchitectureTest {

  @ArchTest
  static final ArchRule noDomainPackages =
      noClasses().should().resideInAPackage("..domain..");

  @ArchTest
  static final ArchRule noServiceDependencies =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.rpe.cardforge.product..",
              "com.rpe.cardforge.cardholder..",
              "com.rpe.cardforge.card..");
}
