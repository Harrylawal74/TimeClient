package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPSocketClient {

    // the client will take the IP Address of the server (in dotted decimal format as an argument)
    // given that for this tutorial both the client and the server will run on the same machine, you can use the loopback address 127.0.0.1
    public static void main(String[] args) throws IOException {

        DatagramSocket socket;
        DatagramPacket packet;

        if (args.length != 1) {
            System.out.println("the hostname of the server is required");
            return;
        }

        int len = 256;
        byte[] buf = new byte[len];

        //****************************************************************************************
        // TODO:
        // add a line below to instantiate the DatagramSocket socket object
        // bind the socket to some port over 1024
        // Note: this is NOT the port we set in the server
        // If you put the same port you will get an exception because
        // the server is also listening to this port and both processes run on the same machine!
        //****************************************************************************************
        //TODO: I ADDED THIS
        socket = new DatagramSocket(4000);

        //****************************************************************************************
        // TODO:
        // Add a line below to get the address from args[0], the argument handed in when the process is started.
        // In Netbeans, add a command line argument by changing the running configuration.
        // The address must be transfomed from a String to an InetAddress (an IP addresse object in Java).
        //****************************************************************************************
        //TODO: I ADDED THIS
        InetAddress address = InetAddress.getByName(args[0]);


        //************************************************************
        // TODO:
        // Add source code below to instantiate a packet using the buf byte array
        // Also, set the IP address and port fields in the packet so that the packet can be sent to the server
        //************************************************************
        //TODO: I ADDED THIS
        packet = new DatagramPacket(buf, len);
        packet.setAddress(address);
        packet.setPort(9000);


        //************************************************************
        // TODO:
        // Send the datagram packet to the server (this is a blocking call) - we do not care about the data that the packet carries.
        // The server will respond to any kind of request (i.e. regardless of the packet payload)
        //************************************************************
        //TODO: I ADDED THIS
        socket.send(packet);

        //**************************************************************************************
        // TODO:
        // add a line of code below to receive a packet containing the server's response
        // we can reuse the DatagramPacket instantiated above - all settable values will be overriden when the receive call completes.
        //**************************************************************************************
        //TODO: I ADDED THIS
        System.out.println("Packet sent?");
        socket.receive(packet);

        // display response
        String received = new String(packet.getData());
        System.out.println("Today's date: " + received.substring(0, packet.getLength()));

        // Close the socket
        socket.close();
    }

}
