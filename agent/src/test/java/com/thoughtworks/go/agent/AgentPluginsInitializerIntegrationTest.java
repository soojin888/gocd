/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.agent;

import com.thoughtworks.go.plugin.infra.PluginManager;
import com.thoughtworks.go.plugin.infra.monitor.DefaultPluginJarLocationMonitor;
import com.thoughtworks.go.util.SystemEnvironment;
import com.thoughtworks.go.util.ZipBuilder;
import com.thoughtworks.go.util.ZipUtil;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;

import static com.thoughtworks.go.agent.launcher.DownloadableFile.AGENT_PLUGINS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/* Some parts are mocked, as in AgentPluginsInitializerTest, but the file system (through ZipUtil) is not. */
@RunWith(MockitoJUnitRunner.class)
public class AgentPluginsInitializerIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Mock
    private PluginManager pluginManager;
    @Mock
    private DefaultPluginJarLocationMonitor pluginJarLocationMonitor;
    @Mock
    private SystemEnvironment systemEnvironment;

    private File directoryForUnzippedPlugins;
    private AgentPluginsInitializer agentPluginsInitializer;

    @Before
    public void setUp() throws Exception {
        agentPluginsInitializer = new AgentPluginsInitializer(pluginManager, pluginJarLocationMonitor, new ZipUtil(), systemEnvironment);

        directoryForUnzippedPlugins = setupUnzippedPluginsDirectoryStructure();
        when(systemEnvironment.get(SystemEnvironment.AGENT_PLUGINS_PATH)).thenReturn(directoryForUnzippedPlugins.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        cleanupAgentPluginsFile();
    }

    @Test
    public void shouldRemoveExistingBundledPluginsBeforeInitializingNewPlugins() throws Exception {
        File existingBundledPlugin = new File(directoryForUnzippedPlugins, "bundled/existing-plugin-1.jar");

        setupAgentsPluginFile().withBundledPlugin("new-plugin-1.jar", "SOME-PLUGIN-CONTENT").done();
        FileUtils.writeStringToFile(existingBundledPlugin, "OLD-CONTENT", UTF_8);

        agentPluginsInitializer.onApplicationEvent(null);

        assertThat(existingBundledPlugin.exists(), is(false));
        assertThat(new File(directoryForUnzippedPlugins, "bundled/new-plugin-1.jar").exists(), is(true));
    }

    @Test
    public void shouldReplaceExistingBundledPluginsWithNewPluginsOfSameName() throws Exception {
        File bundledPlugin = new File(directoryForUnzippedPlugins, "bundled/plugin-1.jar");

        setupAgentsPluginFile().withBundledPlugin("plugin-1.jar", "SOME-NEW-CONTENT").done();
        FileUtils.writeStringToFile(bundledPlugin, "OLD-CONTENT", UTF_8);

        agentPluginsInitializer.onApplicationEvent(null);

        assertThat(bundledPlugin.exists(), is(true));
        assertThat(FileUtils.readFileToString(bundledPlugin, UTF_8), is("SOME-NEW-CONTENT"));
    }

    @Test
    public void shouldRemoveExistingExternalPluginsBeforeInitializingNewPlugins() throws Exception {
        File existingExternalPlugin = new File(directoryForUnzippedPlugins, "external/existing-plugin-1.jar");

        setupAgentsPluginFile().withExternalPlugin("new-plugin-1.jar", "SOME-PLUGIN-CONTENT").done();
        FileUtils.writeStringToFile(existingExternalPlugin, "OLD-CONTENT", UTF_8);

        agentPluginsInitializer.onApplicationEvent(null);

        assertThat(existingExternalPlugin.exists(), is(false));
        assertThat(new File(directoryForUnzippedPlugins, "external/new-plugin-1.jar").exists(), is(true));
    }

    @Test
    public void shouldReplaceExistingExternalPluginsWithNewPluginsOfSameName() throws Exception {
        File externalPlugin = new File(directoryForUnzippedPlugins, "external/plugin-1.jar");

        setupAgentsPluginFile().withExternalPlugin("plugin-1.jar", "SOME-NEW-CONTENT").done();
        FileUtils.writeStringToFile(externalPlugin, "OLD-CONTENT", UTF_8);

        agentPluginsInitializer.onApplicationEvent(null);

        assertThat(externalPlugin.exists(), is(true));
        assertThat(FileUtils.readFileToString(externalPlugin, UTF_8), is("SOME-NEW-CONTENT"));
    }

    @Test
    public void shouldRemoveAnExistingPluginWhenItHasBeenRemovedFromTheServerSide() throws Exception {
        File existingExternalPlugin = new File(directoryForUnzippedPlugins, "external/plugin-1.jar");

        setupAgentsPluginFile().done();
        FileUtils.writeStringToFile(existingExternalPlugin, "OLD-CONTENT", UTF_8);

        agentPluginsInitializer.onApplicationEvent(null);

        assertThat(existingExternalPlugin.exists(), is(false));
    }

    private File setupUnzippedPluginsDirectoryStructure() throws IOException {
        File dir = temporaryFolder.newFolder("unzipped-plugins");
        FileUtils.forceMkdir(new File(dir, "bundled"));
        FileUtils.forceMkdir(new File(dir, "external"));
        return dir;
    }

    private SetupOfAgentPluginsFile setupAgentsPluginFile() throws IOException {
        return new SetupOfAgentPluginsFile(AGENT_PLUGINS.getLocalFile());
    }

    private void cleanupAgentPluginsFile() throws IOException {
        FileUtils.deleteQuietly(AGENT_PLUGINS.getLocalFile());
    }

    private class SetupOfAgentPluginsFile {
        private final File bundledPluginsDir;
        private final File externalPluginsDir;
        private final ZipUtil zipUtil;
        private final File dummyFileSoZipFileIsNotEmpty;
        private File pluginsZipFile;

        public SetupOfAgentPluginsFile(File pluginsZipFile) throws IOException {
            this.pluginsZipFile = pluginsZipFile;
            this.bundledPluginsDir = temporaryFolder.newFolder("bundled");
            this.externalPluginsDir = temporaryFolder.newFolder("external");
            this.dummyFileSoZipFileIsNotEmpty = temporaryFolder.newFile("dummy.txt");
            this.zipUtil = new ZipUtil();
        }

        public SetupOfAgentPluginsFile withBundledPlugin(String pluginFileName, String pluginFileContent) throws IOException {
            FileUtils.writeStringToFile(new File(bundledPluginsDir, pluginFileName), pluginFileContent, UTF_8);
            return this;
        }

        public SetupOfAgentPluginsFile withExternalPlugin(String pluginFileName, String pluginFileContent) throws IOException {
            FileUtils.writeStringToFile(new File(externalPluginsDir, pluginFileName), pluginFileContent, UTF_8);
            return this;
        }

        public File done() throws IOException {
            ZipBuilder zipBuilder = zipUtil.zipContentsOfMultipleFolders(pluginsZipFile, true);
            zipBuilder.add("bundled", bundledPluginsDir).add("external", externalPluginsDir).add("dummy.txt", dummyFileSoZipFileIsNotEmpty).done();
            return pluginsZipFile;
        }
    }
}
