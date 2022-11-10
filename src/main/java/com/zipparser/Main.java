package com.zipparser;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main {
    public static void main(String[] args) throws IOException {

        if (args.length == 2) {

            String command = args[0];
            String arg = args[1];

            if (command.equals("load")) {

                File dir = new File(String.valueOf(arg));
                if (dir.isDirectory()) {

                    String zipName = arg + "\\dirCompressed.zip";

                    File fileToDelete = new File(zipName);
                    if (fileToDelete.exists()) {
                        if (fileToDelete.delete())
                        System.out.println("Zip has been deleted after load");
                    }

                    try {
                        FileOutputStream fos = new FileOutputStream(zipName);
                        ZipOutputStream zipOut = new ZipOutputStream(fos);
                        File fileToZip = new File(arg);

                        zipFile(fileToZip, fileToZip.getName(), zipOut);
                        zipOut.close();
                        fos.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    CloseableHttpClient httpClient = HttpClients.createDefault();
                    HttpPost uploadFile = new HttpPost("http://localhost:8081/api/requests/load");
                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    File f = new File(zipName);
                    try {
                        builder.addBinaryBody(
                                "file",
                                new FileInputStream(f),
                                ContentType.APPLICATION_OCTET_STREAM,
                                f.getName()
                        );
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    HttpEntity multipart = builder.build();
                    uploadFile.setEntity(multipart);
                    CloseableHttpResponse response = null;
                    try {
                        response = httpClient.execute(uploadFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    HttpEntity responseEntity = response.getEntity();
                    String reqId = EntityUtils.toString(responseEntity);

                    System.out.println("Request id: " + reqId);

                } else {
                    System.out.println("Second argument is not a directory");
                }

            } else if (command.equals("result")) {

                CloseableHttpClient httpClient = HttpClients.createDefault();
                HttpGet request = new HttpGet("http://localhost:8081/api/requests/result/" + arg);
                CloseableHttpResponse response = httpClient.execute(request);

                HttpEntity responseEntity = response.getEntity();
                if (response.getStatusLine().getStatusCode() == 200) {
                    String jsonStr = EntityUtils.toString(responseEntity);
                    JSONObject res = new JSONObject(jsonStr);
                    String status = res.getJSONObject("status").getString("name");
                    System.out.println("Request id: " + res.getInt("id") + ", Status: " + status);
                    JSONArray resultList = res.getJSONArray("resultList");

                    if (resultList.length() > 0) {
                        System.out.println("Parsing result:");
                        for (int i = 0; i < resultList.length(); i++) {
                            JSONObject ob = resultList.getJSONObject(i);
                            System.out.println("Content: " + ob.getString("content"));
                            System.out.println("Is found in files: " + ob.getJSONArray("files"));
                        }
                    }
                } else {
                    System.out.println("Server error: " + response.getStatusLine());
                }

            } else {
                System.out.println("Unknown command");
            }

        } else {
            System.out.println("Wrong arguments quantity");
        }




    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }
}
