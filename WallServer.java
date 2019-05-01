package edu.sjsu.cs149;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Implements the logic for a Wall server. See Protocol for details about the protocol being used.
 */
public class WallServer {
    static HashSet<WallServer> servers = new HashSet<>();

    synchronized static void addServer(WallServer server) {
        System.out.println("added " + server);
        servers.add(server);
    }

    synchronized static void removeServer(WallServer server) {
        System.out.println("removed " + server);
        servers.remove(server);
    }

    synchronized static void broadcast(String message) {
        System.out.println("broadcasting: " + message);
        for (WallServer ws: servers) {
            ws.sendMessage(message);
        }
    }

    BufferedInputStream is;
    /* since this is buffered we must remember to call .flush() to send the data */
    BufferedOutputStream os;
    Socket sock;
    LinkedBlockingQueue<String> toSend = new LinkedBlockingQueue<>();
    /* this sentinal indicates that we need to send an ok message back */
    String OK_SENTINAL = "OK";

    WallServer(Socket sock) throws IOException {
        is = new BufferedInputStream(sock.getInputStream());
        os = new BufferedOutputStream(sock.getOutputStream());
        this.sock = sock;
        // drop clients if we don't hear from them for 15 seconds. this is more lenient than we need to be
        // since we count any message received from the clients not just UOK
        sock.setSoTimeout(15000);
    }

    void sendMessage(String message) {
        toSend.add(message);
    }

    public Thread sendThread;
    public Thread recvThread;

    /**
     * we are going to start two threads up: one for receiving messages to broadcast and another to send
     * messages from toSend queue. the threads will use Thread.interrupt() to try to assure mutual destruction
     * if one of them hits an exit condition.
     */
    void start() {
        sendThread = new Thread() {
            public void run() {
                try {
                    String message;
                    while ((message = toSend.take()) != null) {
                        try {
                            /* NOTE: we use == here instead of .equals() so that clients can still send a message
                               with just OK */
                            if (message == OK_SENTINAL) {
                                Protocol.writeShort(os, (short) 4);
                                Protocol.writeShort(os, Protocol.IMOK);
                                os.flush();
                            } else {
                                byte bytes[] = message.getBytes();
                                short len = (short) (4 + bytes.length);
                                Protocol.writeShort(os, len);
                                Protocol.writeShort(os, Protocol.BCAST);
                                os.write(bytes);
                                os.flush();
                            }
                        } catch (IOException e) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                } finally {
                    recvThread.interrupt();
                }
            }
        };

        recvThread = new Thread() {
            public void run() {
		short len;
                try {
                    loop:
                    while ((len = Protocol.readShort(is)) > 0) {
			if (len < 4 || len > 1024) {
                            System.out.println("Invalid length " + len + " from " + WallServer.this);
                            break;
                        }
                        short type = Protocol.readShort(is);
                        switch (type) {
                            case Protocol.UOK:
                                /* immediately queue up an OK response, we don't need to do special processing here */
                                sendMessage(OK_SENTINAL);
                                break;
                            case Protocol.MSG:
                                byte bytes[] = new byte[len - 4];
                                is.readNBytes(bytes, 0, bytes.length);
                                broadcast(new String(bytes));
                                break;
                            default:
                                System.out.println("Unknown message type " + type + " from " + WallServer.this);
                                break loop;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println(this + " is not responding");
                } catch (IOException e) {
                } finally {
                    removeServer(WallServer.this);
                    sendThread.interrupt();
                }
                try {
                    sock.close();
                } catch (IOException e) {
                }
            }
        };
        sendThread.start();
        recvThread.start();
    }

    public String toString() {
        return "WallServer connected to " + sock.getRemoteSocketAddress();
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("USAGE: WallServer port");
            System.exit(1);
        }
	    int port = Integer.parseInt(args[0]);
        try (ServerSocket ss = new ServerSocket(port)) {
            while (true) {
                WallServer ws = new WallServer(ss.accept());
                addServer(ws);
                ws.start();
            }
        }
    }
}
