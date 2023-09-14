package info.kgeorgiy.ja.grunskii;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// NOTE: Can't run code, build is failed:
// TCPProxy.java:25:50
// java: unclosed string literal
// :82:9
//  java: illegal start of expression
public class TCPProxy {
    private final int THREADS_COUNT = 10;
    private final List<TransferData> transfers = new ArrayList<>();
    private final Executor executor = Executors.newFixedThreadPool(THREADS_COUNT);

    public static void main(final String[] args) throws IOException {
        if (args == null || args.length != 1 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("TCPProxy - is proxy for tcp connections from localhost to another host\n" +
                    "//                    Usage: TCPProxy  [tableName]  launch transfers from which written in the table\"");
//            throw new IllegalArgumentException("""
//                    TCPProxy - is proxy for tcp connections from localhost to another host
//                    Usage: TCPProxy  [tableName]  launch transfers from which written in the table""");
        }

        new TCPProxy(args[0]).run();
    }

    public TCPProxy(final String tableName) throws IOException {
        final Path tablePath = Paths.get("tables", tableName);
        // NOTE: Maybe better(faster) to use BufferedReader, then Scanner?
        try (final Scanner scanner = new Scanner(tablePath, StandardCharsets.UTF_8)) {
            readLinesFromFile(scanner);
        } catch (IOException e) {
            throw new IOException("Can't read file");
        } catch (InputMismatchException e) {
            throw new InputMismatchException("File has incorrect structure\n" +
                    "\n" +
                    "                    File may contain only this strings:\n" +
                    "                    [local port]  [remote host]  [remote port]\n" +
                    "\n" +
                    "                    Example of file:\n" +
                    "                    8080  google.com    2345\n" +
                    "                    5432  postgres.com  22");
//            throw new InputMismatchException("""
//                    File has incorrect structure
//
//                    File may contain only this strings:
//                    [local port]  [remote host]  [remote port]
//
//                    Example of file:
//                    8080  google.com    2345
//                    5432  postgres.com  22
//                    """);
        }
    }

    public void run() throws IOException {
        for (TransferData transfer : transfers) {
            // NOTE: No error-handling
            // NOTE: Better to split try to several try-blocks for better error-handling
            try (ServerSocket serverSocket = new ServerSocket(transfer.localPort);
                 Socket localSocket = serverSocket.accept();
                 Socket remoteSocket = new Socket(transfer.remoteHost, transfer.remotePort);

                 InputStream localIn = localSocket.getInputStream();
                 OutputStream localOut = localSocket.getOutputStream();
                 InputStream remoteIn = remoteSocket.getInputStream();
                 OutputStream remoteOut = remoteSocket.getOutputStream()

            ) {
                executor.execute(activateTransfer(localIn, remoteOut));
                executor.execute(activateTransfer(remoteIn, localOut));
            }
        }
    }

    private record TransferData(int localPort, String remoteHost, int remotePort) {
        public static TransferData getTransferData(String line) throws InputMismatchException {
            // NOTE: bufferedReader then Scanner?
            final Scanner scanner = new Scanner(line);
            final int localPort = scanner.nextInt();
            final String remoteHost = scanner.next();
            final int remotePort = scanner.nextInt();
            return new TransferData(localPort, remoteHost, remotePort);
        }
    }

    private void readLinesFromFile(Scanner scanner) throws IOException {
        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine();
            transfers.add(TransferData.getTransferData(line));
        }
    }

    private Runnable activateTransfer(InputStream inputStream, OutputStream outputStream) {
        return () -> {
            byte[] buffer = new byte[2048];
            int readBytesCount;
            try {
                while ((readBytesCount = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readBytesCount);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }
}