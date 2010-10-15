package org.gradle;

import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.internal.compiler.batch.Main;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractClassPathProvider;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompiler;
import org.gradle.api.internal.tasks.compile.IncrementalGroovyCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.FilteringClassLoader;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EclipseGroovyCompilerPlugin implements Plugin<Project> {
    private static URLClassLoader adapterClassLoader;

    public void apply(Project project) {
        project.getTasks().withType(GroovyCompile.class).allTasks(new Action<GroovyCompile>() {
            public void execute(GroovyCompile groovyCompile) {
                groovyCompile.setCompiler(new IncrementalGroovyCompiler(createCompiler(), groovyCompile.getOutputs()));
            }
        });
    }

    private GroovyJavaJointCompiler createCompiler() {
        try {
            return (GroovyJavaJointCompiler) createClassLoader().loadClass(EclipseBackedGroovyJavaJointCompiler.class.getName()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not load compiler adapter.", e);
        }
    }

    private static URLClassLoader createClassLoader() {
        if (adapterClassLoader == null) {
            List<URL> classpath = new ArrayList<URL>();
            for (URL url : ClasspathUtil.getClasspath(EclipseGroovyCompilerPlugin.class.getClassLoader())) {
                if (url.getPath().contains("groovy-eclipse-batch-")) {
                    classpath.add(url);
                }
            }
            File ownClasspath = AbstractClassPathProvider.getClasspathForClass(EclipseGroovyCompilerPlugin.class);
            try {
                classpath.add(ownClasspath.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException("Could not build compiler adapter classpath.", e);
            }

            FilteringClassLoader parentClassLoader = new FilteringClassLoader(GroovyJavaJointCompiler.class.getClassLoader());
            parentClassLoader.allowPackage("org.gradle.api");
            parentClassLoader.allowPackage("org.gradle.util");

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

    public static class EclipseBackedGroovyJavaJointCompiler implements GroovyJavaJointCompiler {
        private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
        private final CompileOptions compileOptions = new CompileOptions();
        private List<File> groovyClasspath = Collections.emptyList();
        private String sourceCompatibility;
        private String targetCompatibility;
        private FileCollection source;
        private File destinationDir;
        private List<File> classpath = Collections.emptyList();

        public GroovyCompileOptions getGroovyCompileOptions() {
            return groovyCompileOptions;
        }

        public void setGroovyClasspath(Iterable<File> classpath) {
            groovyClasspath = GUtil.addLists(classpath);
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

            args.add("-cp");
            args.add(GUtil.join(classpath, File.pathSeparator));
            args.add("-d");
            args.add(destinationDir.getAbsolutePath());
            args.add("-1.5");

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
