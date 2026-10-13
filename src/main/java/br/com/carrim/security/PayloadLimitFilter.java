package br.com.carrim.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound decoded API requests before Jackson materializes arbitrary JSON trees. */
final class PayloadLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 2 * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !Set.of("POST", "PUT", "PATCH").contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > LIMIT) {
            reject(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(LIMIT + 1);
        if (body.length > LIMIT) {
            reject(response);
            return;
        }
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public ServletInputStream getInputStream() {
                        var input = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            public int read() {
                                return input.read();
                            }

                            public int read(byte[] bytes, int offset, int length) {
                                return input.read(bytes, offset, length);
                            }

                            public boolean isFinished() {
                                return input.available() == 0;
                            }

                            public boolean isReady() {
                                return true;
                            }

                            public void setReadListener(ReadListener listener) {
                                try {
                                    if (!isFinished()) listener.onDataAvailable();
                                    if (isFinished()) listener.onAllDataRead();
                                } catch (IOException error) {
                                    listener.onError(error);
                                }
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                    }
                },
                response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json");
        response.getWriter()
                .write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Pedido excede o limite permitido.\"}");
    }
}
