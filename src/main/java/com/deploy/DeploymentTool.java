package com.deploy;

import com.jcraft.jsch.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DeploymentTool {

    private static final String HOST = "10.72.25.208";
    private static final String USER = "cloud-user";
    private static final String KEY = "C:\\Users\\parmahaj\\Documents\\Projects\\ncsmano1.pem";

    private static final String LOCAL_CPIO = "C:\\Users\\parmahaj\\Downloads\\CLI_NEI 30\\CLI_NEI\\JAVA_CLI_ANY_V1-1.1.4.cpio - Copy.Z";
    private static final String LOCAL_SPEC = "C:\\Users\\parmahaj\\Downloads\\CLI_NEI 30\\CLI_NEI\\CLI_ANY_V1_nei - Copy.specification";

    private static final String REMOTE_PATH = "/data/cloud-user/om/packages/";
    private static final String RELEASE_PATH = "/data/cloud-user/om/release_area";
    private static final String INSTALL_PATH = "/data/cloud-user/om/install";

    private static final String PRODUCT = "JAVA_CLI_ANY_V1";
    private static final String VERSION = "1.1.4";

    private static BufferedWriter logWriter;

    public static void main(String[] args) {
        try {
            initLogger();
            log("DEPLOYMENT STARTED");

            JSch jsch = new JSch();
            jsch.addIdentity(KEY);

            Session session = jsch.getSession(USER, HOST, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            log("Connected to server");

            // ================= UPLOAD =================
            uploadFile(session, LOCAL_CPIO);
            uploadFile(session, LOCAL_SPEC);

            // ================= PERMISSIONS =================
            execute(session, "chmod 777 " + REMOTE_PATH + "*");

            // ================= ACTUAL STEPS =================

            // Step 1: Switch to om + go to release_area
            execute(session, "sudo su - om -c 'cd " + RELEASE_PATH + " && pwd'");

            // Step 2: List installed products
            execute(session, "sudo su - om -c 'cd " + RELEASE_PATH + " && ./manage_releases -l'");

            // Step 3: Uninstall
            execute(session, "sudo su - om -c 'cd " + RELEASE_PATH + " && ./manage_releases --uninstall " + PRODUCT + "'");

            // Step 4: List files
            execute(session, "sudo su - om -c 'cd " + RELEASE_PATH + " && ls -lrt'");

            // Step 5: Remove old release
            execute(session, "sudo su - om -c 'cd " + RELEASE_PATH + " && rm -rf " + PRODUCT + "_REL_" + VERSION + "'");

            // Step 6: Install (FINAL COMMAND)
            execute(session,
                    "sudo su - om -c 'cd " + RELEASE_PATH +
                            " && ./manage_releases --hot -r " + RELEASE_PATH +
                            " -s " + REMOTE_PATH + "CLI_ANY_V1_nei.specification" +
                            " -i " + INSTALL_PATH +
                            " -p " + REMOTE_PATH + "'"
            );

            log("DEPLOYMENT SUCCESS");
            session.disconnect();

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLogger();
        }
    }

    // ================= FILE UPLOAD =================
    private static void uploadFile(Session session, String localFile) throws Exception {
        log("Uploading: " + localFile);

        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();

        sftp.put(localFile, REMOTE_PATH);

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
        try {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String line = "[" + time + "] " + msg;

            System.out.println(line);
            logWriter.write(line);
            logWriter.newLine();
            logWriter.flush();
        } catch (Exception ignored) {}
    }

    private static void closeLogger() {
        try {
            if (logWriter != null) logWriter.close();
        } catch (Exception ignored) {}
    }
}