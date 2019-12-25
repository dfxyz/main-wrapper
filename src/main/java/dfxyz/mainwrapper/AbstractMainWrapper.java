package dfxyz.mainwrapper;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class AbstractMainWrapper {
    private static final String APP_PROCESS_UUID = "app.process.uuid";

    private static final String SINGLE_QUOTE = "\'";
    private static final String DOUBLE_QUOTE = "\"";

    private static final boolean onWindows = System.getProperty("os.name").toLowerCase().contains("windows");

    private static String quote(final String arg) {
        String quotedArg = arg.trim();

        while (quotedArg.startsWith(SINGLE_QUOTE) && quotedArg.endsWith(SINGLE_QUOTE)
                || quotedArg.startsWith(DOUBLE_QUOTE) && quotedArg.endsWith(DOUBLE_QUOTE)) {
            quotedArg = quotedArg.substring(1, quotedArg.length() - 1);
        }

        final StringBuilder sb = new StringBuilder();
        if (quotedArg.contains(DOUBLE_QUOTE)) {
            if (quotedArg.contains(SINGLE_QUOTE)) {
                throw new IllegalArgumentException("cannot handle single and double quotes in a single argument");
            }
            if (onWindows) {
                quotedArg = quotedArg.replace(DOUBLE_QUOTE, "\\\"");
                return sb.append(DOUBLE_QUOTE).append(quotedArg).append(DOUBLE_QUOTE).toString();
            } else {
                return sb.append(SINGLE_QUOTE).append(quotedArg).append(SINGLE_QUOTE).toString();
            }
        }
        if (quotedArg.contains(SINGLE_QUOTE) || quotedArg.contains(" ")) {
            return sb.append(DOUBLE_QUOTE).append(quotedArg).append(DOUBLE_QUOTE).toString();
        }
        return quotedArg;
    }

    @SuppressWarnings("WeakerAccess")
    protected abstract String processUuidFilename();

    private String processUuid(final boolean createIfNotExists) {
        final File file = new File(processUuidFilename());
        if (file.exists()) {
            try (final BufferedReader reader = new BufferedReader(new FileReader(processUuidFilename()))) {
                return reader.readLine();
            } catch (final IOException e) {
                throw new RuntimeException("failed to read process uuid file", e);
            }
        }

        if (!createIfNotExists) {
            System.out.println("failed to read process uuid file; application may never be started");
            System.exit(0);
        }

        try {
            if (!file.createNewFile()) {
                throw new RuntimeException("process uuid file has been created concurrently");
            }
            final String uuid = UUID.randomUUID().toString();
            try (final FileWriter writer = new FileWriter(file)) {
                writer.write(uuid);
            }
            return uuid;
        } catch (final IOException e) {
            throw new RuntimeException("failed to write process uuid file", e);
        }
    }

    public final void invoke(final String[] args) {
        final int length = args.length;
        if (length > 0) {
            final String arg0 = args[0];
            switch (arg0) {
                case "run":
                    mainFunction(Arrays.copyOfRange(args, 1, length));
                    return;
                case "start":
                    start(Arrays.copyOfRange(args, 1, length));
                    return;
                case "stop":
                    stop();
                    return;
                case "restart":
                    restart(Arrays.copyOfRange(args, 1, length));
                    return;
                case "status":
                    status();
                    return;
            }
        }
        usage();
    }

    @SuppressWarnings("WeakerAccess")
    protected abstract void mainFunction(final String[] args);

    private String pid(final String processUuid) {
        final List<String> args = new LinkedList<>();
        if (onWindows) {
            args.add("wmic");
            args.add("process");
            args.add("where");
            args.add(quote("commandline like '%" + APP_PROCESS_UUID + "%'"));
            args.add("get");
            args.add("commandline");
            args.add("/value");
        } else {
            args.add("sh");
            args.add("-c");
            args.add("ps ax | grep " + APP_PROCESS_UUID);
        }

        String pid = null;
        try {
            final Process process = new ProcessBuilder().command(args).start();
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (true) {
                    final String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (line.contains(processUuid)) {
                        if (onWindows) {
                            pid = "";
                            break;
                        } else {
                            final Pattern pattern = Pattern.compile("([0-9]+)\\s.*");
                            final Matcher matcher = pattern.matcher(line);
                            if (!matcher.find()) {
                                throw new RuntimeException("failed to get application's pid");
                            }
                            pid = matcher.group(1);
                            break;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (final IOException | InterruptedException e) {
            throw new RuntimeException("failed to scan running processes", e);
        }
        return pid;
    }

    private void start(final String[] args) {
        start(processUuid(true), args);
    }

    private void start(final String processUuid, final String[] args) {
        if (pid(processUuid) != null) {
            System.out.println("application is running; operation aborted");
            return;
        }

        final List<String> arguments = new LinkedList<>();

        if (onWindows) {
            arguments.add("cmd.exe");
            arguments.add("/k");
            arguments.add("start");
            arguments.add("\"title\"");
            arguments.add("/b");
        }

        final File javaHome = new File(System.getProperty("java.home"));
        final File java = onWindows ? new File(javaHome, "bin/java.exe") : new File(javaHome, "bin/java");
        if (!java.isFile()) {
            throw new RuntimeException("failed to find java's executable file");
        }
        arguments.add(quote(java.getAbsolutePath()));

        for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            arguments.add(quote(arg));
        }
        arguments.add("-D" + APP_PROCESS_UUID + "=" + processUuid);

        final String[] commands = System.getProperty("sun.java.command", "").split(" ");
        if (commands.length < 1) {
            throw new RuntimeException("Failed to get main class or jar name");
        }
        final String mainClassOrJarName = commands[0];
        if (mainClassOrJarName.endsWith(".jar")) {
            arguments.add("-jar");
        }
        arguments.add(quote(mainClassOrJarName));

        arguments.add("run");

        for (final String arg : args) {
            arguments.add(quote(arg));
        }

        final ProcessBuilder builder = new ProcessBuilder();
        builder.environment().put("CLASSPATH", System.getProperty("java.class.path"));
        try {
            builder.command(arguments).start();
            System.out.println("application started");
        } catch (final IOException e) {
            throw new RuntimeException("failed to create application process", e);
        }
    }

    private void stop() {
        stop(processUuid(false));
    }

    private void stop(final String processUuid) {
        final List<String> arguments = new LinkedList<>();

        if (onWindows) {
            arguments.add("wmic");
            arguments.add("process");
            arguments.add("where");
            arguments.add(quote("commandline like '%" + APP_PROCESS_UUID + "=" + processUuid + "%'"));
            arguments.add("call");
            arguments.add("terminate");
            try {
                final Process process = new ProcessBuilder().command(arguments).start();
                final int code = process.waitFor();
                System.out.println("application stopped with code " + code);
            } catch (final IOException | InterruptedException e) {
                throw new RuntimeException("failed to stop application", e);
            }
            return;
        }

        final String pid = pid(processUuid);
        if (pid == null) {
            System.out.println("application is not running");
            return;
        }

        arguments.add("kill");
        arguments.add(pid);
        try {
            final int code = new ProcessBuilder().command(arguments).start().waitFor();
            System.out.println("application stopped with code " + code);
        } catch (final IOException | InterruptedException e) {
            throw new RuntimeException("failed to stop application", e);
        }
    }

    private void restart(final String[] args) {
        final String processUuid = processUuid(true);
        stop(processUuid);
        start(processUuid, args);
    }

    private void status() {
        final String processUuid = processUuid(false);
        final String pid = pid(processUuid);
        if (pid != null) {
            System.out.println("application is running");
        } else {
            System.out.println("application is not running");
        }
    }

    private void usage() {
        System.out.println("Usage: run [args...]");
        System.out.println("   or: start [args...]");
        System.out.println("   or: restart [args...]");
        System.out.println("   or: stop");
        System.out.println("   or: status");
    }
}
