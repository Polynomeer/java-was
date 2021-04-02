package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import db.DataBase;
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
            String cookie = "";
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
                if (line.contains("Cookie")) {
                    cookie = line.split(" ")[1];
                    log.debug("request cookie : {}", cookie);
                }
                log.debug("header : {}", line);
            }

//            if (method.equals("GET") && url.contains("/user/create")) {
//                String queryString = url.split("\\?")[1];
//                user = parseUrl(queryString);
//            }


            if (method.equals("GET")) {
                if (url.startsWith("/css")) {
                    DataOutputStream dos = new DataOutputStream(out);
                    byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                    response200HeaderCss(dos, body.length);
                    responseBody(dos, body);
                } else if (url.startsWith("/user/list.html")) {
                    if (cookie.contains("logined=true")) {
                        DataOutputStream dos = new DataOutputStream(out);
                        byte[] body = buildUserListResponseBody();

                        log.debug("body : {}", body);
                        response200Header(dos, body.length);
                        responseBody(dos, body);
                    } else {
                        DataOutputStream dos = new DataOutputStream(out);
                        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                        response302Header(dos, "/user/login.html");
                        responseBody(dos, body);
                    }
                } else if (url.startsWith("/user/logout")) {
                    DataOutputStream dos = new DataOutputStream(out);
                    String responseCookie = "logined=false";
                    response302HeaderWithCookie(dos, "/user/login.html", responseCookie);
                } else {
                    DataOutputStream dos = new DataOutputStream(out);
                    byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                    response200Header(dos, body.length);
                    responseBody(dos, body);
                }
            }
            if (method.equals("POST")) {
                if (url.startsWith("/create")) {
                    requestBody = IOUtils.readData(bufferedReader, contentLength);
                    log.debug("request body : {}", requestBody);
                    User user = parseUrl(requestBody);
                    DataBase.addUser(user);

                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                    log.debug("response header : {}", dos);
                } else if (url.startsWith("/user/login")) {
                    DataOutputStream dos = new DataOutputStream(out);
                    String queryString = IOUtils.readData(bufferedReader, contentLength);
                    Map<String, String> map = HttpRequestUtils.parseQueryString(queryString);

                    User user = DataBase.findUserById(map.get("userId"));
                    String responseCookie = "logined=false";

                    if (user == null || !user.matchPassword(map.get("password"))) {
                        response302HeaderWithCookie(dos, "/user/login_failed.html", responseCookie);
                    } else { // login success
                        responseCookie = "logined=true";
                        response302HeaderWithCookie(dos, "/index.html", responseCookie);
                    }
                }
            }


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

    private void response200HeaderCss(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String url, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + url + "\r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + url + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
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

    private byte[] buildUserListResponseBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n" +
                "<html lang=\"kr\">\n" +
                "<head>\n" +
                "    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>SLiPP Java Web Programming</title>\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n" +
                "    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
                "    <!--[if lt IE 9]>\n" +
                "    <script src=\"//html5shim.googlecode.com/svn/trunk/html5.js\"></script>\n" +
                "    <![endif]-->\n" +
                "    <link href=\"../css/styles.css\" rel=\"stylesheet\">\n" +
                "</head>\n" +
                "<body>\n" +
                "<nav class=\"navbar navbar-fixed-top header\">\n" +
                "    <div class=\"col-md-12\">\n" +
                "        <div class=\"navbar-header\">\n" +
                "\n" +
                "            <a href=\"../index.html\" class=\"navbar-brand\">SLiPP</a>\n" +
                "            <button type=\"button\" class=\"navbar-toggle\" data-toggle=\"collapse\" data-target=\"#navbar-collapse1\">\n" +
                "                <i class=\"glyphicon glyphicon-search\"></i>\n" +
                "            </button>\n" +
                "\n" +
                "        </div>\n" +
                "        <div class=\"collapse navbar-collapse\" id=\"navbar-collapse1\">\n" +
                "            <form class=\"navbar-form pull-left\">\n" +
                "                <div class=\"input-group\" style=\"max-width:470px;\">\n" +
                "                    <input type=\"text\" class=\"form-control\" placeholder=\"Search\" name=\"srch-term\" id=\"srch-term\">\n" +
                "                    <div class=\"input-group-btn\">\n" +
                "                        <button class=\"btn btn-default btn-primary\" type=\"submit\"><i class=\"glyphicon glyphicon-search\"></i></button>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "            </form>\n" +
                "            <ul class=\"nav navbar-nav navbar-right\">\n" +
                "                <li>\n" +
                "                    <a href=\"#\" class=\"dropdown-toggle\" data-toggle=\"dropdown\"><i class=\"glyphicon glyphicon-bell\"></i></a>\n" +
                "                    <ul class=\"dropdown-menu\">\n" +
                "                        <li><a href=\"https://slipp.net\" target=\"_blank\">SLiPP</a></li>\n" +
                "                        <li><a href=\"https://facebook.com\" target=\"_blank\">Facebook</a></li>\n" +
                "                    </ul>\n" +
                "                </li>\n" +
                "                <li><a href=\"../user/list.html\"><i class=\"glyphicon glyphicon-user\"></i></a></li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</nav>\n" +
                "<div class=\"navbar navbar-default\" id=\"subnav\">\n" +
                "    <div class=\"col-md-12\">\n" +
                "        <div class=\"navbar-header\">\n" +
                "            <a href=\"#\" style=\"margin-left:15px;\" class=\"navbar-btn btn btn-default btn-plus dropdown-toggle\" data-toggle=\"dropdown\"><i class=\"glyphicon glyphicon-home\" style=\"color:#dd1111;\"></i> Home <small><i class=\"glyphicon glyphicon-chevron-down\"></i></small></a>\n" +
                "            <ul class=\"nav dropdown-menu\">\n" +
                "                <li><a href=\"../user/profile.html\"><i class=\"glyphicon glyphicon-user\" style=\"color:#1111dd;\"></i> Profile</a></li>\n" +
                "                <li class=\"nav-divider\"></li>\n" +
                "                <li><a href=\"#\"><i class=\"glyphicon glyphicon-cog\" style=\"color:#dd1111;\"></i> Settings</a></li>\n" +
                "            </ul>\n" +
                "            \n" +
                "            <button type=\"button\" class=\"navbar-toggle\" data-toggle=\"collapse\" data-target=\"#navbar-collapse2\">\n" +
                "            \t<span class=\"sr-only\">Toggle navigation</span>\n" +
                "            \t<span class=\"icon-bar\"></span>\n" +
                "            \t<span class=\"icon-bar\"></span>\n" +
                "            \t<span class=\"icon-bar\"></span>\n" +
                "            </button>            \n" +
                "        </div>\n" +
                "        <div class=\"collapse navbar-collapse\" id=\"navbar-collapse2\">\n" +
                "            <ul class=\"nav navbar-nav navbar-right\">\n" +
                "                <li class=\"active\"><a href=\"../index.html\">Posts</a></li>\n" +
                "                <li><a href=\"../user/login.html\" role=\"button\">로그인</a></li>\n" +
                "                <li><a href=\"../user/form.html\" role=\"button\">회원가입</a></li>\n" +
                "                <li><a href=\"#\" role=\"button\">로그아웃</a></li>\n" +
                "                <li><a href=\"#\" role=\"button\">개인정보수정</a></li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</div>\n" +
                "\n" +
                "<div class=\"container\" id=\"main\">\n" +
                "   <div class=\"col-md-10 col-md-offset-1\">\n" +
                "      <div class=\"panel panel-default\">\n" +
                "          <table class=\"table table-hover\">\n" +
                "              <thead>\n" +
                "                <tr>\n" +
                "                    <th>#</th> <th>사용자 아이디</th> <th>이름</th> <th>이메일</th><th></th>\n" +
                "                </tr>\n" +
                "              </thead>\n" +
                "              <tbody>\n" +
                "                <tr>\n");

        Iterator<User> iterator = DataBase.findAll().iterator();
        int i = 1;
        while (iterator.hasNext()) {
            User user = iterator.next();
            sb.append("<th scope=\"row\">")
                    .append(i++)
                    .append("</th> <td>")
                    .append(user.getUserId())
                    .append("</td>").append("<td>")
                    .append(user.getName())
                    .append("</td>").append("<td>")
                    .append(user.getEmail())
                    .append("</td><td><a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a></td>\n");
        }
        sb.append("                </tr>\n" +
                "              </tbody>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</div>\n" +
                "\n" +
                "<!-- script references -->\n" +
                "<script src=\"../js/jquery-2.2.0.min.js\"></script>\n" +
                "<script src=\"../js/bootstrap.min.js\"></script>\n" +
                "<script src=\"../js/scripts.js\"></script>\n" +
                "\t</body>\n" +
                "</html>");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
