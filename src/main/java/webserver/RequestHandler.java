package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;

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
                    new InputStreamReader(in, "UTF-8"));

            String line = bufferedReader.readLine();
            log.debug("start line : {}", line);

            String url = getUrl(line);
            log.debug("url : {}", url);

            while (!line.equals("")) {
                line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                log.debug("header : {}", line);
            }

            User user;
            if (url.contains("/user/create")) {
                user = parseUrl(url);
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private User parseUrl(String url) {
        String[] tokens = url.split("\\?");
        String queryString = tokens[1];

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
