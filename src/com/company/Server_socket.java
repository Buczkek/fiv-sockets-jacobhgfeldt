import org.dreambot.api.Client;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.login.LoginUtility;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.trade.Trade;
import org.dreambot.api.methods.trade.TradeUser;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.randoms.RandomEvent;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.wrappers.widgets.message.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ScriptManifest(
        author = "Jacob",
        description = "Requirement: None.",
        category = Category.CRAFTING,
        version = 1.00,
        name = "Server_socket"
)



public class Server_socket extends AbstractScript implements ChatListener {

    Area grandExchangeArea = new Area(new Tile(3160, 3490), new Tile(3168, 3485));

    private Server server;
    private int farmerWorld;
//    private boolean traded = true;

    @Override // Infinite loop
    public int onLoop() {

        sleep(randomNum(500, 1000));

        // In this script you have the server (mule). We have 2 statements.
        // The first statement is: Did the server(mule) recieve an integer (indicating that a goldfarmer is requesting the mule to come online)
        // The second statement is: There is no request from any goldfarmer, which means that the mule logs off (or stays logged off).

//        if(traded) {
//            farmerWorld = server.getTradeWorld();
//            traded = false;
//        }

        farmerWorld = server.getTradeWorld();


        if (farmerWorld != -1) {
            if (Client.getCurrentWorld() != farmerWorld && !Client.isLoggedIn()) {
                LoginUtility.changeWorld(farmerWorld);
            }
            if (Client.getCurrentWorld() == farmerWorld && !Client.isLoggedIn()) {
                getRandomManager().enableSolver(RandomEvent.LOGIN);
                sleepUntil(Client::isLoggedIn, 30000);
            }
            if (Client.getCurrentWorld() == farmerWorld && Client.isLoggedIn()) {
                if (!grandExchangeArea.contains(Players.localPlayer())) {
                    sleep(randomNum(1000, 3000));
                    Walking.walk(grandExchangeArea.getRandomTile());
                }

                if (!grandExchangeArea.contains(Players.localPlayer())) {
                    sleep(randomNum(1000, 2000));
                    Walking.walk(grandExchangeArea.getRandomTile());
                }


                // If we're at the mulearea, we trade.
                if (grandExchangeArea.contains(Players.localPlayer())) {
                    log("test");
                    if (Trade.isOpen()) {
                        log("test1");
                        if (Trade.hasAcceptedTrade(TradeUser.THEM)) {
                            log("Test2");
                            sleep(randomNum(1000, 2000));
                            Trade.acceptTrade();
//                            traded = true;
                            server.moveQueue();
                            log("Trade completed!");
                        }
                    }
                }
            }
        }


        if (farmerWorld == -1){
            if (Client.isLoggedIn()) {
                sleep(randomNum(1000, 3000));
                Tabs.logout();
                sleepUntil(() -> !Client.isLoggedIn(), 10000);
            }
            if (!Client.isLoggedIn()) {
                // Waiting for a goldfarmer to send its integer.
                sleep(randomNum(1500,3000));
            }
        }

        return 0;
    }

    public void onMessage(Message message) {
        log("test3");
        String trade_username = message.getUsername();
        log(trade_username);
        log("test4");

        if (!Trade.isOpen()) {
            log("test5");
            sleep(randomNum(1000, 3000));
            Trade.tradeWithPlayer(trade_username);
            sleepUntil(Trade::isOpen, 10000);
        }
    }

    public void onStart() {
        log("Bot started. Logging in.");
        sleep(randomNum(1000, 2000));

        // initiating server socket (change the numer for different port)
        server = new Server(1234);
        server.start();
    }


    public void onExit() {
        log("Bot Ended");
    }


    //Random number generator for sleeping (mimics human "brain afk").
    public int randomNum(int i, int k) {
        int number = (int) (Math.random() * (k - i + 1)) + i;
        return number;
    }

    private class Server extends Thread{
        private ServerSocket serverSocket;
        private List<ClientThread> threads = new ArrayList<>();
        private AtomicLong counter = new AtomicLong();
        List<ClientThread> queue = new ArrayList<>();
        private int currentTradeSocket = -1;


        public Server(int port) {
            try {
                this.serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void listen(){
            log("Socket server listening");
            while(true){
                try {
                    Socket clientSocket = serverSocket.accept();
                    int new_id = (int) counter.getAndIncrement();
                    log("NEW CONNECTION: (" + clientSocket.toString() +") ID: " + new_id);
                    ClientThread thread = new ClientThread(clientSocket, this, new_id);
                    thread.start();
                    threads.add(thread);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            listen();
        }

        public List<Integer> getAllSockets(){
            List<Integer> list = new ArrayList<>();
            for(int i=0;i<counter.intValue();i++){
                list.add(i);
            }
            return list;
        }


        public List<Integer> getConnectedIds(){
            socketCleanup();
            List<Integer> result = new ArrayList<>();
            for(ClientThread thread : threads){
                result.add(thread.getSocketId());
            }
            return result;
        }

        public List<Integer> getDisconnectedIds(){
            List<Integer> connected = getConnectedIds();
            List<Integer> result = new ArrayList<>();
            for(int i=0;i<counter.intValue();i++){
                boolean isConnected = false;
                for (int id : connected) {
                    if (i == id) {
                        isConnected = true;
                        break;
                    }
                }
                if (!isConnected){
                    result.add(i);
                }
            }
            return result;
        }

        public void socketCleanup(){
            List<ClientThread> alive = new ArrayList<>();
            for (ClientThread thread : threads){
                if (thread.isAlive()){
                    alive.add(thread);
                }
            }
            threads = alive;
        }

        // If there is no one to trade with returns -1 else returns id of the client
        public int getNextTrade(){
            if (queue.isEmpty()){
                return -1;
            }
            ClientThread temp = queue.remove(0);
            currentTradeSocket = temp.getSocketId();
            temp.tradeMessage();
            return temp.worldId;
        }

        public int getTradeWorld(){
            if (queue.isEmpty()){
                return -1;
            } else {
                return queue.get(0).worldId;
            }
        }

        public void moveQueue(){
            queue.remove(0);
        }

    }

    private class ClientThread extends Thread{
        private Socket socket;
        private Server server;
        private PrintWriter writer;
        private int socketId;
        private int worldId;
        private boolean isRunning = true;

        public ClientThread(Socket socket, Server server, int id){
            this.socket = socket;
            this.server = server;
            this.socketId = id;
        }

        @Override
        public void run(){
            try {
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                this.writer = new PrintWriter(output, true);
                while(isRunning){
                    String line = reader.readLine();
                    if(line == null){
                        log("[" + socketId + "] DISCONNECTED");
                        this.kill();
                    }
                    if (line.equals("hello")){
                        send(Integer.toString(socketId));
                    } else {
                        worldId = Integer.valueOf(line);
                        queueUp(worldId);
                    }
                    log("[" + socketId + "] Received: " + line);
                }
            } catch (SocketException e) {
                log("[" + socketId + "] INTERRUPTED");
                this.kill();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Integer getSocketId(){
            return socketId;
        }

        public void kill(){
            isRunning = false;
        }

        public void queueUp(int worldId){
            log(socketId + " queued up on world " + worldId);
            server.queue.add(this);
        }

        public void tradeMessage(){
            send("trade");
            log("Sent trade message to client");
        }

        public void send(String message){
            writer.println(message);
        }
    }

}

