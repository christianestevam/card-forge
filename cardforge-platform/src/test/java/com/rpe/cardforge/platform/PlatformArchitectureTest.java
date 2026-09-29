package com.rpe.cardforge.platform;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class PlatformArchitectureTest {
 @Test void platformHasNoBusinessDependencies() {
 noClasses().that().resideInAPackage("com.rpe.cardforge.platform..")
 .should().dependOnClassesThat().resideInAnyPackage("com.rpe.cardforge.product..", "com.rpe.cardforge.card..", "com.rpe.cardforge.cardholder..", "..domain..")
 .check(new ClassFileImporter().importPackages("com.rpe.cardforge.platform"));
 }
}
