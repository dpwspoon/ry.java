/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.ry.internal;
import static java.lang.String.format;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.util.Arrays.binarySearch;
import static java.util.Arrays.sort;
import static org.agrona.IoUtil.tmpDirName;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.function.Predicate;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.agrona.concurrent.SigIntBarrier;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.reaktivity.nukleus.Configuration;
import org.reaktivity.nukleus.Controller;
import org.reaktivity.reaktor.Reaktor;

public final class RyMain
{
    public static void main(final String[] args) throws Exception
    {
        CommandLineParser parser = new DefaultParser();

        Options options = new Options();
        options.addOption(Option.builder("d").longOpt("directory").hasArg().desc("configuration directory").build());
        options.addOption(Option.builder("h").longOpt("help").desc("print this message").build());
        options.addOption(Option.builder("n").longOpt("nukleus").hasArgs().desc("nukleus name").build());
        options.addOption(Option.builder("c").longOpt("controller").hasArgs().desc("controller name").build());
        options.addOption(Option.builder("clean").desc("clean").build());
        options.addOption(Option.builder("s").longOpt("script").hasArgs().desc("execution script").build());
        options.addOption(Option.builder("v").longOpt("version").desc("version information").build());

        CommandLine cmdline = parser.parse(options, args);

        if (cmdline.hasOption("version"))
        {
            final Package p = Package.getPackage("org.reaktivity.ry.internal");
            final String version = p.getSpecificationVersion();
            System.out.println("Version: "+ version);
        }
        if (cmdline.hasOption("help"))
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("reaktor", options);
        }
        else
        {
            String directory = cmdline.getOptionValue("directory",
                    format("%sorg.reaktivity.reaktor%s", tmpDirName(), File.separator));
            String[] nuklei = cmdline.getOptionValues("nukleus");
            String[] controllers = cmdline.getOptionValues("controller");

            Properties properties = new Properties();
            properties.setProperty(Configuration.DIRECTORY_PROPERTY_NAME, directory);

            Configuration config = new RyConfiguration(properties);

            Predicate<String> includeNuklei = name -> true;
            if (nuklei != null)
            {
                Comparator<String> comparator = (o1, o2) -> o1.compareTo(o2);
                sort(nuklei, comparator);
                includeNuklei = name -> binarySearch(nuklei, name, comparator) >= 0;
            }

            Predicate<Class<? extends Controller>> includeControllers = c -> true;
            if (controllers != null)
            {
                includeControllers = c -> binarySearch(controllers, c.getName()) >= 0;
            }

            if (cmdline.hasOption("clean"))
            {
                Files.walk(config.directory(), FOLLOW_LINKS)
                .filter(path ->
                {
                    final int count = path.getNameCount();
                    return "control".equals(path.getName(count - 1).toString()) ||
                            (count >= 2 && "streams".equals(path.getName(count - 2).toString()));
                })
                .map(Path::toFile)
                .forEach(File::delete);
            }

            try (Reaktor reaktor = Reaktor.builder()
                .config(config)
                .nukleus(includeNuklei)
                .controller(includeControllers)
                .errorHandler(ex -> ex.printStackTrace(System.err))
                .build()
                .start())
            {
                System.out.println("Started in " + config.directory());

                final ScriptEngineManager manager = new ScriptEngineManager();
                final ScriptEngine engine = manager.getEngineByName("nashorn");

                Bindings bindings = engine.createBindings();
                reaktor.controllerKinds().forEach(k ->
                {
                    Controller c = reaktor.controller(k);
                    final String name = c.name().replaceAll("-", "_") + "Controller";
                    System.out.println("Adding " + name);
                    bindings.put(name, c);
                });
                bindings.put("reaktor", reaktor);

                String script = cmdline.getOptionValue("script");
                if (script == null)
                {
                    new SigIntBarrier().await();
                }
                else
                {
                    engine.eval(new InputStreamReader(new FileInputStream(script)), bindings);
                    new SigIntBarrier().await();
                }

            }
        }
    }

}
