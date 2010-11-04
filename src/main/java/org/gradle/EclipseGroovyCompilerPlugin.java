/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle;

import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.internal.compiler.batch.Main;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompiler;
import org.gradle.api.internal.tasks.compile.IncrementalGroovyCompiler;
import org.gradle.api.internal.tasks.compile.IncrementalJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.FilteringClassLoader;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.gradle.api.Plugin} that configures each instance of {@link org.gradle.api.tasks.compile.Compile} and
 * {@link org.gradle.api.tasks.compile.GroovyCompile} to use the groovy eclipse compiler instead of the javac or groovyc
 * compiler.
 */
public class EclipseGroovyCompilerPlugin implements Plugin<Project> {
    private static URLClassLoader adapterClassLoader;
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseGroovyCompilerPlugin.class);

    public void apply(final Project project) {
        project.getTasks().withType(GroovyCompile.class).allTasks(new Action<GroovyCompile>() {
            public void execute(GroovyCompile groovyCompile) {
                groovyCompile.setCompiler(new IncrementalGroovyCompiler(createGroovyCompiler(), groovyCompile.getOutputs()));
            }
        });
        project.getTasks().withType(Compile.class).allTasks(new Action<Compile>() {
            public void execute(Compile compile) {
                ProjectInternal projectInternal = (ProjectInternal) project;
                compile.setJavaCompiler(new IncrementalJavaCompiler(createJavaCompiler(), projectInternal.getServices().getFactory(AntBuilder.class), compile.getOutputs()));
            }
        });
    }

    private JavaCompiler createJavaCompiler() {
        // Classloader hackery to work around Gradle's poor classloader model. This will get fixed after Gradle 0.9 is out.
        try {
            return (JavaCompiler) createClassLoader().loadClass(EclipseBackedGroovyJavaJointCompiler.class.getName()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not load compiler adapter.", e);
        }
    }

    private GroovyJavaJointCompiler createGroovyCompiler() {
        // Classloader hackery to work around Gradle's poor classloader model. This will get fixed after Gradle 0.9 is out.
        try {
            return (GroovyJavaJointCompiler) createClassLoader().loadClass(EclipseBackedGroovyJavaJointCompiler.class.getName()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not load compiler adapter.", e);
        }
    }

    private static URLClassLoader createClassLoader() {
        if (adapterClassLoader == null) {
            List<URL> classpath = new ArrayList<URL>();
            List<URL> pluginClasspath = ClasspathUtil.getClasspath(EclipseGroovyCompilerPlugin.class.getClassLoader());
            for (URL url : pluginClasspath) {
                if (url.getPath().contains("groovy-eclipse-")) {
                    classpath.add(url);
                }
            }
            LOGGER.debug("Using compiler adapter classpath: {}", classpath);

            FilteringClassLoader parentClassLoader = new FilteringClassLoader(GroovyJavaJointCompiler.class.getClassLoader());
            parentClassLoader.allowPackage("org.gradle.api");
            parentClassLoader.allowPackage("org.gradle.util");
            parentClassLoader.allowPackage("org.slf4j");

            // check class is not visible from parent class loader
            try {
                parentClassLoader.loadClass(EclipseBackedGroovyJavaJointCompiler.class.getName());
                throw new RuntimeException("unexpected classloader set up.");
            } catch (ClassNotFoundException e) {
                // expected
            }

            adapterClassLoader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), parentClassLoader);
        }
        return adapterClassLoader;
    }

    public static class EclipseBackedGroovyJavaJointCompiler implements GroovyJavaJointCompiler, JavaCompiler {
        private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
        private final CompileOptions compileOptions = new CompileOptions();
        private String sourceCompatibility;
        private String targetCompatibility;
        private FileCollection source;
        private File destinationDir;
        private List<File> classpath = Collections.emptyList();

        public GroovyCompileOptions getGroovyCompileOptions() {
            return groovyCompileOptions;
        }

        public void setGroovyClasspath(Iterable<File> classpath) {
            // Don't care
        }

        public void setDependencyCacheDir(File file) {
            // Don't care
        }

        public CompileOptions getCompileOptions() {
            return compileOptions;
        }

        public void setSourceCompatibility(String sourceCompatibility) {
            this.sourceCompatibility = sourceCompatibility;
        }

        public void setTargetCompatibility(String targetCompatibility) {
            this.targetCompatibility = targetCompatibility;
        }

        public void setSource(FileCollection source) {
            this.source = source;
        }

        public void setDestinationDir(File file) {
            destinationDir = file;
        }

        public void setClasspath(Iterable<File> classpath) {
            this.classpath = GUtil.addLists(classpath);
        }

        public WorkResult execute() {
            PrintWriter stdout = new PrintWriter(new OutputStreamWriter(System.out));
            PrintWriter stderr = new PrintWriter(new OutputStreamWriter(System.err));

            Main compiler = new Main(stdout, stderr, false, Collections.<Object, Object>emptyMap(), new CompilationProgressImpl());
            List<String> args = new ArrayList<String>();

            classpath.add(destinationDir);

            args.add("-cp");
            args.add(GUtil.join(classpath, File.pathSeparator));
            args.add("-d");
            args.add(destinationDir.getAbsolutePath());
            if (GUtil.isTrue(sourceCompatibility)) {
                args.add("-source");
                args.add(sourceCompatibility);
            }
            if (GUtil.isTrue(targetCompatibility)) {
                args.add("-target");
                args.add(targetCompatibility);
            }
            if (compileOptions.isDebug()) {
                args.add("-g");
            }
            args.addAll(compileOptions.getCompilerArgs());

            // TODO - add the remaining options

            LOGGER.info("Using compile args: {}", args);

            for (File file : source) {
                args.add(file.getAbsolutePath());
            }
            boolean ok = compiler.compile(args.toArray(new String[args.size()]));

            stdout.flush();
            stderr.flush();

            if (!ok) {
                throw new RuntimeException("Compile failed.");
            }

            return new WorkResult() {
                public boolean getDidWork() {
                    return true;
                }
            };
        }
    }

    private static class CompilationProgressImpl extends CompilationProgress {
        @Override
        public void begin(int totalWork) {
        }

        @Override
        public void done() {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void setTaskName(String name) {
        }

        @Override
        public void worked(int increment, int workRemaining) {
        }
    }
}
