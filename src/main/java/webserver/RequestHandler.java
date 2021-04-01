package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));

            String line = bufferedReader.readLine();
            log.debug("start line : {}", line);

            String[] tokens = line.split(" ");
            String method = tokens[0].toUpperCase();
            log.debug("method : {}", method);

            String url = getUrl(line);
            log.debug("url : {}", url);

            String requestBody = "";
            int contentLength = 0;
            while (!line.equals("")) {
                line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                if (line.contains("Content-Length")) {
                    contentLength = Integer.parseInt(line.split(" ")[1]);
                    log.debug("content length : {}", contentLength);
                }
                log.debug("header : {}", line);
            }


            User user;
            if (method.equals("GET") && url.contains("/user/create")) {
                String queryString = url.split("\\?")[1];
                user = parseUrl(queryString);
            }

            if (method.equals("POST")) {
                requestBody = IOUtils.readData(bufferedReader, contentLength);
                log.debug("request body : {}", requestBody);
                user = parseUrl(requestBody);
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private User parseUrl(String queryString) {
        Map<String, String> map = HttpRequestUtils.parseQueryString(queryString);

        User user = new User(map.get("userId"), map.get("password"), map.get("name"), map.get("email"));
        log.debug(user.toString());

        return user;
    }

    private String getUrl(String line) {
        return line.split(" ")[1];
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
