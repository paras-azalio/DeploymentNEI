package com.deploy;

import com.jcraft.jsch.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeploymentTool {

//    private static final String HOST = "10.72.25.208";
//    private static final String USER = "cloud-user";
//    private static final String KEY = "C:\\Users\\parmahaj\\Documents\\Projects\\ncsmano1.pem";
//
//    private static final String LOCAL_CPIO = "C:\\Users\\parmahaj\\Downloads\\CLI_NEI 30\\CLI_NEI\\JAVA_CLI_ANY_V1-1.1.4.cpio - Copy.Z";
//    private static final String LOCAL_SPEC = "C:\\Users\\parmahaj\\Downloads\\CLI_NEI 30\\CLI_NEI\\CLI_ANY_V1_nei - Copy.specification";
//
//    private static final String REMOTE_PATH = "/data/cloud-user/om/packages/";
//    private static final String RELEASE_PATH = "/data/cloud-user/om/release_area";
//    private static final String INSTALL_PATH = "/data/cloud-user/om/install";
//
//    private static final String PRODUCT = "JAVA_CLI_ANY_V1";
//    private static final String VERSION = "1.1.4";

    private static BufferedWriter logWriter;

    private static String[] extractProductAndVersion(String cpioPath) {
        String fileName = new File(cpioPath).getName();

        Pattern pattern = Pattern.compile("^((.*)-([0-9]+(?:\\.[0-9]+)*))\\.cpio\\.Z$");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            String product  = matcher.group(2);   // JAVA_CLI_ANY_V1
            String version  = matcher.group(3);   // 1.1.5


            return new String[]{product, version};
        } else {
            throw new RuntimeException("Invalid CPIO filename format: " + fileName);
        }
    }

    private static void validateArgs(String... args) {
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                log("ERROR: Missing required argument!");
                throw new RuntimeException("Missing required argument");
            }
        }
    }

    public static void main(String[] args) {
        try {
            initLogger();
            String HOST = getArg(args, "--host");
            String USER = getArg(args, "--user");
            String KEY = getArg(args, "--key");
            String LOCAL_CPIO = getArg(args, "--local-cpio");
            String LOCAL_SPEC = getArg(args, "--local-spec");
            String REMOTE_PATH = getArg(args, "--remote-path");
            String RELEASE_PATH = getArg(args, "--release-path");
            String INSTALL_PATH = getArg(args, "--install-path");

            String[] result = extractProductAndVersion(LOCAL_CPIO);

            String PRODUCT = result[0];
            String VERSION = result[1];
            String fullName = new File(LOCAL_CPIO).getName();

            String specFileName = new File(LOCAL_SPEC).getName();

            log("Extracted PRODUCT: " + PRODUCT);
            log("Extracted VERSION: " + VERSION);

            validateArgs(HOST, USER, KEY, LOCAL_CPIO, LOCAL_SPEC,
                    REMOTE_PATH, RELEASE_PATH, INSTALL_PATH, PRODUCT, VERSION);

            log("Starting Deployment");

            JSch jsch = new JSch();
            jsch.addIdentity(KEY);

            Session session = null;

            session = jsch.getSession(USER, HOST, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

//            try {
//                session = jsch.getSession(USER, HOST, 22);
//                session.setConfig("StrictHostKeyChecking", "no");
//                session.connect(30000); // timeout
//
//                log("Connected to server");
//
//                // your logic...
//
//            } catch (Exception e) {
//                log("FATAL ERROR: " + e.getMessage());
//                throw e;
//            } finally {
//                if (session != null && session.isConnected()) {
//                    session.disconnect();
//                    log("Session disconnected");
//                }
//            }

            uploadFile(session, LOCAL_CPIO, REMOTE_PATH);
            uploadFile(session, LOCAL_SPEC, REMOTE_PATH);

            prepareUploadedFiles(session, fullName, LOCAL_SPEC, REMOTE_PATH);
            log("Permissions and ownership updated");

            execute(session,
                    "sudo -u om bash -c 'cd " + RELEASE_PATH + " && ./manage_releases -l'"
            );

            if (!LOCAL_CPIO.contains(PRODUCT)) {
                log("WARNING: Product name and CPIO file mismatch!");
            }

            execute(session,
                    "sudo -u om bash -c 'cd " + REMOTE_PATH + " && zcat "+fullName+" | cpio -t'"
            );

            executeWithAutoEnter(session,
                    "sudo -u om bash -c 'cd " + REMOTE_PATH + " && ./manage_releases --uninstall " + PRODUCT
            );


            execute(session,
                    "sudo -u om bash -c 'cd " + REMOTE_PATH + " && ls -lrt"
            );

            log("Using spec file: " + specFileName);

            execute(session,
                    "sudo -u om bash -c 'rm -rf " + RELEASE_PATH + "/" + PRODUCT + "_REL_" + VERSION + "'"
            );

            execute(session,
                    "sudo -u om bash -c 'cd " + RELEASE_PATH + " && ./manage_releases --hot -r " + RELEASE_PATH +
                            " -s " + REMOTE_PATH + specFileName +
                            " -i " + INSTALL_PATH +
                            " -p " + REMOTE_PATH + "'"
            );

            log("DEPLOYMENT SUCCESS");
            session.disconnect();

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        }finally {
            closeLogger();
        }
    }
    private static String getArg(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(key)) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static void prepareUploadedFiles(Session session,
                                             String localCpio,
                                             String localSpec,
                                             String remotePath) throws Exception {

        String cpioName = new File(localCpio).getName();
        String specName = new File(localSpec).getName();

        log("Preparing uploaded files...");
        log("CPIO: " + cpioName);
        log("SPEC: " + specName);

        execute(session,
                "sudo bash -c \"chmod 777 " +
                        remotePath + cpioName + " " +
                        remotePath + specName + "\""
        );

        execute(session,
                "sudo bash -c \"chown om:cloud-user " +
                        remotePath + cpioName + " " +
                        remotePath + specName + "\""
        );

        log("Permissions and ownership updated successfully");
    }

    private static void readShellOutput(BufferedReader reader) throws IOException {
        long waitTime = 3000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < waitTime) {
            while (reader.ready()) {
                String line = reader.readLine();
                if (line != null) {
                    log(line);
                }
            }
        }
    }

    private static void executeInteractive(Session session,
                                           String PRODUCT,
                                           String VERSION,
                                           String RELEASE_PATH,
                                           String REMOTE_PATH,
                                           String INSTALL_PATH) throws Exception {

        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.setPty(true);

        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        channel.connect();

        PrintWriter writer = new PrintWriter(out, true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String[] commands = new String[] {
                "sudo su - om",
                "bash",
                "cd release_area/",
                "./manage_releases -l",
                "./manage_releases --uninstall " + PRODUCT,
                "ls -lrt",
                "rm -rf " + PRODUCT + "_REL_" + VERSION,
                "./manage_releases --hot -r " + RELEASE_PATH +
                        " -s " + REMOTE_PATH + "CLI_ANY_V1_nei.specification" +
                        " -i " + INSTALL_PATH +
                        " -p " + REMOTE_PATH,
                "exit"
        };

        for (String cmd : commands) {
            log("Executing: " + cmd);

            writer.println(cmd);
            writer.flush();

            Thread.sleep(3000);

            readShellOutput(reader);
        }
        channel.disconnect();
    }

    // ================= FILE UPLOAD =================
    private static void uploadFile(Session session, String localFile, String remotePath) throws Exception {

        File file = new File(localFile);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + localFile);
        }

        log("Uploading: " + localFile);

        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();

        try {
            sftp.put(localFile, remotePath);
            log("Upload completed: " + localFile);
        } catch (Exception e) {
            log("Upload failed: " + e.getMessage());
            throw e;
        } finally {
            sftp.disconnect();
        }
    }

    // ================= EXECUTE COMMAND =================
    private static void execute(Session session, String command) throws Exception {
        log("Executing: " + command);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        InputStream in = channel.getInputStream();
        InputStream err = channel.getErrStream();

        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        BufferedReader errReader = new BufferedReader(new InputStreamReader(err));

        StringBuilder fullOutput = new StringBuilder(); // 🔥 NEW

        String line;

        // Read STDOUT
        while ((line = reader.readLine()) != null) {
            log(line);
            fullOutput.append(line).append("\n");
        }

        // Read STDERR
        while ((line = errReader.readLine()) != null) {
            log("ERROR_STREAM: " + line);
            fullOutput.append(line).append("\n");
        }

        // Wait for command completion
        while (!channel.isClosed()) {
            Thread.sleep(1000);
        }

        int exitStatus = channel.getExitStatus();
        log("Exit Status: " + exitStatus);

        channel.disconnect();

        String output = fullOutput.toString();

        // ================= SMART HANDLING =================

        // 1. Ignore uninstall if product not installed
        if (command.contains("--uninstall") && output.contains("not installed")) {
            log("WARNING: Uninstall skipped (product not installed)");
            return;
        }

        // 2. Invalid package → HARD FAIL
        if (output.contains("Invalid cpio package")) {
            throw new RuntimeException("FATAL: Invalid CPIO package. Check your file.");
        }

        // 3. Missing internal files
        if (output.contains("No such file or directory")) {
            throw new RuntimeException("FATAL: Package structure broken (missing files inside cpio).");
        }

        // 4. Generic failure
        if (exitStatus != 0) {
            throw new RuntimeException("Command failed: " + command);
        }
        System.out.println("");
        System.out.println("");
    }
    // ================= LOGGER =================
    private static void initLogger() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        logWriter = new BufferedWriter(new FileWriter("deployment_" + timestamp + ".log"));
    }

    private static void log(String msg) {
        try {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String logMsg = "[" + time + "] " + msg;

            System.out.println(logMsg);

            if (logWriter != null) {
                logWriter.write(logMsg);
                logWriter.newLine();
                logWriter.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeLogger() {
        try {
            if (logWriter != null) logWriter.close();
        } catch (Exception ignored) {}
    }
}