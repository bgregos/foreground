package de.aaschmid.taskwarrior;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import de.aaschmid.taskwarrior.config.TaskwarriorAuthentication;
import de.aaschmid.taskwarrior.config.TaskwarriorConfiguration;
import de.aaschmid.taskwarrior.message.TaskwarriorMessage;
import de.aaschmid.taskwarrior.ssl.KeyStoreBuilder;
import de.aaschmid.taskwarrior.ssl.SslContextFactory;

public class TaskwarriorClient {

    private static final Charset CHARSET_TRANSFER_MESSAGE = Charset.forName("UTF-8");

    private static final String SEPARATOR_HEADER_NAME_VALUE = ": ";
    private static final Pattern PATTERN_HEADER_LINE = Pattern.compile("^(.+?)" + SEPARATOR_HEADER_NAME_VALUE + "(.+)$");

    private final TaskwarriorConfiguration config;
    private final SSLContext sslContext;

    public TaskwarriorClient(TaskwarriorConfiguration config) {
        this.config = requireNonNull(config, "'configuration' must not be null.");

        // @formatter:off
        KeyStore keyStore = new KeyStoreBuilder()
                .withCaCertFile(config.getCaCertFile())
                .withPrivateKeyCertFile(config.getPrivateKeyCertFile())
                .withPrivateKeyFile(config.getPrivateKeyFile())
                .build();
        // @formatter:on
        this.sslContext = SslContextFactory.getInstance(keyStore, KeyStoreBuilder.KEYSTORE_PASSWORD);
    }

    public TaskwarriorMessage sendAndReceive(TaskwarriorMessage message) throws IOException {
        requireNonNull(message, "'message' must not be null.");

        try (Socket socket = sslContext.getSocketFactory().createSocket(config.getServerHost(), config.getServerPort());
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream()) {
            send(message, out);
            return receive(in);
        }
    }

    private void send(TaskwarriorMessage message, OutputStream out) throws IOException {
        byte[] requestBytes = createByteArraysFor(message);

        out.write(requestBytes);
        out.flush();
    }

    private TaskwarriorMessage receive(InputStream in) throws IOException {
        int messageLength = receiveRemainingMessageLengthFromFourByteBigEndianBinaryByteCountPrefix(in);

        byte[] data = readMessageAsByteArray(in, messageLength);

        String dataAsString = new String(data, CHARSET_TRANSFER_MESSAGE);
        // System.out.println(dataAsString);
        return parseResponse(dataAsString);
    }

    private byte[] createByteArraysFor(TaskwarriorMessage message) {
        // @formatter:off
        String messageData = Stream.concat(
                Stream.of(
                        createHeadersFor(config.getAuthentication()),
                        message.getHeaders()
                    ).map(Map::entrySet).flatMap(Set::stream).map(e -> e.getKey() + SEPARATOR_HEADER_NAME_VALUE + e.getValue()),
                Stream.of("", message.getPayload().orElse(""))
            ).collect(Collectors.joining("\n"));
        // @formatter:on

        byte[] bytes = messageData.getBytes(CHARSET_TRANSFER_MESSAGE);
        return addFourByteBigEndianBinaryByteCountMessageLengthPrefix(bytes);
    }

    private int receiveRemainingMessageLengthFromFourByteBigEndianBinaryByteCountPrefix(InputStream in) throws IOException {
        byte[] sizeBytes = new byte[4];
        int length = in.read(sizeBytes);
        if (length != 4) {
            throw new TaskwarriorClientException("Could not read first for bytes of message containing encoded message length.");
        }
        return ((sizeBytes[0] << 24) | (sizeBytes[1] << 16) | (sizeBytes[2] << 8) | sizeBytes[3]) - 4;
    }

    private byte[] readMessageAsByteArray(InputStream in, int messageLength) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int readCount;
        int remaining = messageLength;
        byte[] buffer = new byte[1024];
        while ((readCount = in.read(buffer)) != -1) {
            out.write(buffer, 0, readCount);
            remaining -= readCount;
        }

        if (remaining > 0) {
            throw new TaskwarriorClientException("Could not retrieve complete message, remaining '%d' of '%d' bytes.", remaining,
                    messageLength);
        }

        out.flush();
        return out.toByteArray();
    }

    private TaskwarriorMessage parseResponse(String message) {
        int index = message.indexOf("\n\n");

        String header = message.substring(0, index);
        String payload = message.substring(index + 2, message.length());

        Map<String, String> headers = parseHeaders(header);
        if (payload.isEmpty() || "\n".equals(payload)) {
            return new TaskwarriorMessage(headers);
        }
        return new TaskwarriorMessage(headers, payload);
    }

    private Map<String, String> createHeadersFor(TaskwarriorAuthentication auth) {
        Map<String, String> result = new HashMap<>();
        result.put("org", auth.getOrganistion());
        result.put("user", auth.getUser());
        result.put("key", auth.getKey().toString());
        return result;
    }

    private byte[] addFourByteBigEndianBinaryByteCountMessageLengthPrefix(byte[] bytes) {
        byte[] result = new byte[bytes.length + 4];
        System.arraycopy(bytes, 0, result, 4, bytes.length);

        int l = result.length;
        result[0] = (byte) (l >> 24);
        result[1] = (byte) (l >> 16);
        result[2] = (byte) (l >> 8);
        result[3] = (byte) l;
        return result;
    }

    private Map<String, String> parseHeaders(String header) {
        Map<String, String> headers = new HashMap<>();
        for (String headerLine : header.split("\n")) {
            Matcher matcher = PATTERN_HEADER_LINE.matcher(headerLine);
            if (matcher.matches()) {
                String name = matcher.group(1);
                String value = matcher.group(2);
                headers.put(name, value);

            } else {
                throw new TaskwarriorClientException("Regex pattern '%s' does not match header line '%s'.", PATTERN_HEADER_LINE.pattern(),
                        headerLine);
            }
        }
        return headers;
    }
}
