package pl.epsi;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component(
        role = AbstractMavenLifecycleParticipant.class,
        hint = "jderef"
)
public class DerefLifecycle extends AbstractMavenLifecycleParticipant {

    private static final Logger log =
            LoggerFactory.getLogger(DerefLifecycle.class);

    @Override
    public void afterProjectsRead(MavenSession session) {
        for (MavenProject project : session.getProjects()) {
            try {
                enable(project);
            } catch (IOException e) {
                throw new RuntimeException("Failed enabling JDeref", e);
            }
        }
    }

    private Path extractPatch(MavenProject project) throws IOException {
        // Use target/jderef-patch, which survives rebuilds but is cleared by 'clean'
        Path patchDir = Path.of(project.getBuild().getDirectory(), "jderef-patch");

        // Only extract if it doesn't exist
        if (Files.exists(patchDir)) {
            return patchDir;
        }

        Files.createDirectories(patchDir);
        try (InputStream in = getClass().getResourceAsStream("/jderef-patch.zip")) {
            if (in == null) throw new IOException("Missing jderef-patch.zip");

            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path output = patchDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zis, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return patchDir;
    }

    private void enable(MavenProject project) throws IOException {
        Path patchDir = extractPatch(project);
        String patchArg = "-J--patch-module=jdk.compiler=" + patchDir.toAbsolutePath();

        // 1. Inject into PluginManagement if it exists
        if (project.getPluginManagement() != null) {
            Plugin pluginMgmt = project.getPluginManagement().getPluginsAsMap()
                    .get("org.apache.maven.plugins:maven-compiler-plugin");
            if (pluginMgmt != null) {
                injectInto(pluginMgmt, patchArg);
                for (PluginExecution exec : pluginMgmt.getExecutions()) {
                    injectInto(exec, patchArg);
                }
            }
        }

        // 2. Inject into the actively resolved Build Plugins
        Plugin compiler = project.getBuild().getPluginsAsMap()
                .get("org.apache.maven.plugins:maven-compiler-plugin");

        if (compiler == null) {
            compiler = new Plugin();
            compiler.setGroupId("org.apache.maven.plugins");
            compiler.setArtifactId("maven-compiler-plugin");
            compiler.setVersion("3.14.0");

            project.getBuild().addPlugin(compiler);
            log.warn("Created maven-compiler-plugin");
        }

        injectInto(compiler, patchArg);
        for (PluginExecution exec : compiler.getExecutions()) {
            injectInto(exec, patchArg);
        }
    }

    private void injectInto(Object container, String patchArg) {
        Xpp3Dom config = null;

        // Extract or initialize the configuration DOM based on container type
        if (container instanceof Plugin plugin) {
            if (plugin.getConfiguration() instanceof Xpp3Dom dom) {
                config = dom;
            } else {
                config = new Xpp3Dom("configuration");
                plugin.setConfiguration(config);
            }
        } else if (container instanceof PluginExecution exec) {
            if (exec.getConfiguration() instanceof Xpp3Dom dom) {
                config = dom;
            } else {
                config = new Xpp3Dom("configuration");
                exec.setConfiguration(config);
            }
        }

        if (config != null) {
            Xpp3Dom fork = findOrCreate(config, "fork");
            fork.setValue("true");

            Xpp3Dom compilerArgs = findOrCreate(config, "compilerArgs");

            // CRITICAL: Tells Maven to append lists rather than overriding them
            compilerArgs.setAttribute("combine.children", "append");

            // Prevent duplicate injections if life-cycles re-evaluate
            boolean exists = false;
            for (Xpp3Dom child : compilerArgs.getChildren("arg")) {
                if (patchArg.equals(child.getValue())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                Xpp3Dom arg = new Xpp3Dom("arg");
                arg.setValue(patchArg);
                compilerArgs.addChild(arg);
            }
        }
    }

    private Xpp3Dom findOrCreate(Xpp3Dom parent, String name) {
        // Optimised using getChild() instead of a manual loop
        Xpp3Dom child = parent.getChild(name);
        if (child != null) {
            return child;
        }
        Xpp3Dom created = new Xpp3Dom(name);
        parent.addChild(created);
        return created;
    }
}