package com.deploy;

import com.jcraft.jsch.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    public static void main(String[] args) {
        try {
            String HOST = getArg(args, "--host");
            String USER = getArg(args, "--user");
            String KEY = getArg(args, "--key");
            String LOCAL_CPIO = getArg(args, "--local-cpio");
            String LOCAL_SPEC = getArg(args, "--local-spec");
            String REMOTE_PATH = getArg(args, "--remote-path");
            String RELEASE_PATH = getArg(args, "--release-path");
            String INSTALL_PATH = getArg(args, "--install-path");
            String PRODUCT = getArg(args, "--product");
            String VERSION = getArg(args, "--version");

            log("DEPLOYMENT STARTED");

            JSch jsch = new JSch();
            jsch.addIdentity(KEY);

            Session session = jsch.getSession(USER, HOST, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            log("Connected to server");

            uploadFile(session, LOCAL_CPIO, REMOTE_PATH);
            uploadFile(session, LOCAL_SPEC, REMOTE_PATH);

            execute(session, "chmod 777 " + REMOTE_PATH + "*");

            executeInteractive(session, PRODUCT, VERSION, RELEASE_PATH, REMOTE_PATH, INSTALL_PATH);

            log("DEPLOYMENT SUCCESS");
            session.disconnect();

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
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

            while (reader.ready()) {
                log(reader.readLine());
            }
        }

        channel.disconnect();
    }
    // ================= FILE UPLOAD =================
    private static void uploadFile(Session session, String localFile, String remotePath) throws Exception {
        log("Uploading: " + localFile);

        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();

        sftp.put(localFile, remotePath);

        sftp.disconnect();
        log("Upload completed: " + localFile);
    }

    // ================= EXECUTE COMMAND =================
    private static void execute(Session session, String command) throws Exception {
        log("Executing: " + command);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setErrStream(System.err);

        InputStream in = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;

        while ((line = reader.readLine()) != null) {
            log(line);
        }

        channel.disconnect();
    }

    // ================= LOGGER =================
    private static void initLogger() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        logWriter = new BufferedWriter(new FileWriter("deployment_" + timestamp + ".log"));
    }

    private static void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + time + "] " + msg);
    }

    private static void closeLogger() {
        try {
            if (logWriter != null) logWriter.close();
        } catch (Exception ignored) {}
    }
}