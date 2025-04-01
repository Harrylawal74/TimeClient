package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.InputMismatchException;

public class UDPClient {
    private static final int SERVER_PORT = 1069;
    private static final int BUFFER_SIZE = 516;
    private static final int DATA_SIZE = 512;
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 5;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("enter server IP address: ");
        String serverIp = scanner.nextLine();

        try {
            InetAddress serverAddress = InetAddress.getByName(serverIp);
            int choice =0;

            while (true) {
                System.out.println("1. press 1 to store file");
                System.out.println("2. press 2 to retrieve file");
                try {
                    choice = scanner.nextInt();
                    scanner.nextLine();
                    if (choice == 1 || choice == 2) {
                        break;
                    } else {
                        System.out.println("invalid option, please enter 1 or 2.");
                    }
                } catch (InputMismatchException e) {
                    System.out.println("invalid input, please enter a number (1 or 2).");
                    scanner.nextLine();
                }
            }

            System.out.print("enter filename: ");
            String filename= scanner.nextLine();

            if (choice == 1) {
                writeFile(serverAddress, filename);
            } else {
                readFile(serverAddress, filename);
            }
        } catch (UnknownHostException e) {
            System.err.println("couldnt get IP address from hostname: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void readFile(InetAddress serverAddress, String filename) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);

        byte[] rrqPacket = createRequestPacket(1, filename, "octet");
        DatagramPacket requestPacket = new DatagramPacket(rrqPacket, rrqPacket.length, serverAddress, SERVER_PORT);
        socket.send(requestPacket);

        byte[] dataBuffer = new byte[BUFFER_SIZE];
        FileOutputStream fileOutStream = new FileOutputStream(filename);

        int blockN = 1;
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
            socket.receive(receivePacket);

            int opcode = dataBuffer[1];
            if (opcode == 3) {
                int receivedBlockN = ((dataBuffer[2] & 0xff) << 8) | (dataBuffer[3] & 0xff);
                if (receivedBlockN == blockN) {
                    int dataLength = receivePacket.getLength() - 4;
                    fileOutStream.write(dataBuffer, 4, dataLength);
                    sendAck(socket, serverAddress, receivedBlockN);

                    if (dataLength < DATA_SIZE) {
                        break;
                    }
                    blockN++;
                }
            } else if (opcode == 5) {
                System.out.println("error from server: " + new String(dataBuffer, 4, receivePacket.getLength() - 5));
                break;
            }
        }
        fileOutStream.close();
        socket.close();
    }

    private static void writeFile(InetAddress serverAddress, String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("no file found: " + filename);
            return;
        }

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);

        byte[] wrqPacket = createRequestPacket(2, filename, "octet");
        DatagramPacket requestPacket = new DatagramPacket(wrqPacket, wrqPacket.length, serverAddress, SERVER_PORT);
        socket.send(requestPacket);
        handleServerResponseDuringWrite(socket, serverAddress, filename);
        socket.close();
    }

    private static void handleServerResponseDuringWrite(DatagramSocket socket, InetAddress serverAddress, String filename) throws IOException {
        FileInputStream fileInStream = new FileInputStream(filename);
        byte[] dataBuffer = new byte[DATA_SIZE];
        int blockN = 1;
        boolean ackReceived = false;

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            int bytesRead = fileInStream.read(dataBuffer);
            if (bytesRead == -1) {
                fileInStream.close();
                return;
            }

            byte[] dataPacket = createDataPacket(blockN, dataBuffer, bytesRead);
            DatagramPacket dataPacketToSend = new DatagramPacket(dataPacket, dataPacket.length, serverAddress, SERVER_PORT);
            socket.send(dataPacketToSend);

            try {
                DatagramPacket ackPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                socket.receive(ackPacket);
                if (isValidAck(ackPacket.getData(), blockN)) {
                    ackReceived = true;
                    break;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("timeout while waiting for ACK #" + blockN + ". Retrying... Attempt " + (retry + 1));
                int newTimeout = TIMEOUT_MS * (retry + 1);
                socket.setSoTimeout(newTimeout);
            }
        }

        if (!ackReceived) {
            throw new IOException("failed to receive valid ACK for block #" + blockN);
        }

        fileInStream.close();
    }

    private static byte[] createRequestPacket(int opcode, String filename, String mode) {
        byte[] modeBytes =mode.getBytes();
        byte[] filenameBytes= filename.getBytes();
        byte[] packet = new byte[2 + filenameBytes.length + 1 + modeBytes.length + 1];
        packet[0] = 0;
        packet[1] =(byte) opcode;
        System.arraycopy(filenameBytes, 0, packet, 2, filenameBytes.length);
        packet[filenameBytes.length + 2] = 0;
        System.arraycopy(modeBytes, 0, packet, filenameBytes.length + 3, modeBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private static byte[] createDataPacket(int blockN, byte[] data, int length) {
        byte[] dataPacket = new byte[4 + length];
        dataPacket[0] =0;
        dataPacket[1] =3;
        dataPacket[2] =(byte) (blockN >> 8);
        dataPacket[3]= (byte) (blockN);
        System.arraycopy(data, 0, dataPacket, 4, length);
        return dataPacket;
    }

    private static void sendAck(DatagramSocket socket, InetAddress address, int blockN) throws IOException {
        byte[] ackPacket= {0, 4, (byte) (blockN >> 8), (byte) (blockN)};
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length, address, SERVER_PORT);
        socket.send(packet);
    }

    private static boolean isValidAck(byte[] ackData, int blockN) {
        int ackBlockN = ((ackData[2] & 0xff) << 8) | (ackData[3] & 0xff);
        return ackData[1] == 4 && ackBlockN == blockN;
    }
}






