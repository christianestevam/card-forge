package com.rpe.cardforge.product;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class ArchitectureTest { @Test void domainDoesNotDependOnFrameworks() {
 noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
 .resideInAnyPackage("org.springframework..","jakarta.persistence..","software.amazon..","com.fasterxml.jackson..")
 .check(new ClassFileImporter().importPackages("com.rpe.cardforge.product")); } }
