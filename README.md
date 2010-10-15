What is it?
-----------

A Gradle plugin that configures the Groovy plugin to use the Groovy Eclipse compiler.

Building
--------

You'll need to use Gradle 0.9-rc-1.

Run `gradle install` to build the plugin into the local maven repository.

Using the plugin
----------------

Add the following to your build script:

    buildscript {
        repositories {
            mavenRepo(urls: uri("${System.getProperty('user.home')}/.m2/repository"))
        }
        dependencies {
            classpath 'org.gradle:eclipseCompiler:0.1-SNAPSHOT'
        }
    }

    apply plugin: 'groovy'
    apply plugin: 'groovy-eclipse'
