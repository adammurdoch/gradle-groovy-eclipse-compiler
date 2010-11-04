What is it?
-----------

A Gradle plugin that configures the Java and Groovy plugins to use the Groovy Eclipse compiler instead of the default
javac and groovyc compilers.

Why would I want use it?
------------------------

[stubless joint groovy/java compilation](http://contraptionsforprogramming.blogspot.com/2010/09/where-are-all-my-stubs.html)

Building the plugin
-------------------

You'll need to use Gradle 0.9-rc-2 or later. It does not work with earlier Gradle releases.

Run `gradle install` to build the plugin and install it into the local maven repository.

Using the plugin
----------------

The plugin works with Gradle 0.9-rc-2 or later. To use it, you need to:

1.  load the plugin from the local maven repository using a `buildscript { }` section
2.  apply the `groovy-eclipse` plugin.
3.  apply any of the `java`, `java-base`, `groovy` or `groovy-base` plugins. The plugin does not do anything on its own.

Here's an example:

    buildscript {
        repositories {
            mavenRepo(urls: uri("${System.getProperty('user.home')}/.m2/repository"))
        }
        dependencies {
            classpath 'org.gradle:groovy-eclipse:0.1-SNAPSHOT'
        }
    }

    apply plugin: 'groovy' // or apply plugin: 'java'
    apply plugin: 'groovy-eclipse'

### What doesn't work?

-   There are some issues with AST transformations.
-   Some compile options are not wired up yet.
