import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.trade.Trade;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.GameObject;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.security.spec.RSAOtherPrimeInfo;
import java.util.Arrays;

@ScriptManifest(
        author = "Jacob",
        description = "Requirement: None.",
        category = Category.CRAFTING,
        version = 1.00,
        name = "Client_Script"
)



public class Client_socket extends AbstractScript {

    Area grandExchangeArea = new Area(new Tile(3160, 3490), new Tile(3168, 3485));
    Area treeArea = new Area(3131, 3441, 3143, 3423);

    private ServerConnection serverConnection;
    private boolean traded = true;


    @Override // Infinite loop
    public int onLoop() {

        if (!Inventory.contains("Logs")) {
            if (!treeArea.contains(Players.localPlayer())) {
                sleep(randomNum(1000, 3000));
                Walking.walk(treeArea.getRandomTile());
            }

            if (treeArea.contains(Players.localPlayer()) && !Inventory.contains("Logs")) {
                GameObject Tree = GameObjects.closest("Tree");
                if (Tree != null) {
                    if (Inventory.isItemSelected()) {
                        Inventory.deselect();
                    }
                    log("Chopping tree");
                    sleep(randomNum(750, 1500));
                    Tree.interact("Chop down");
                    sleep(randomNum(3500, 4500));
                    sleepUntil(() -> Players.localPlayer().getAnimation() != 879, 50000);
                    sleepUntil(() -> Players.localPlayer().getAnimation() != 877, 50000);
                    sleep(randomNum(1000, 4000));
                }
            }
        }

        if (Inventory.contains("Logs")) {

            // This is your only job on the Client (Goldfarmers). Make sure that this integer (farmWorld) gets sent to the server.
            // Here you have to send the following integer to the Server (mule)
            int farmerWorld = Worlds.getCurrentWorld();

            if (traded) {
                serverConnection.queueUp(farmerWorld);
                this.traded = false;
            }

            if (!grandExchangeArea.contains(Players.localPlayer())) {
                sleep(randomNum(1000, 3000));
                Walking.walk(grandExchangeArea.getRandomTile());
            }
            if (grandExchangeArea.contains(Players.localPlayer())) {
                if (!Trade.isOpen()) {
                    sleep(randomNum(2500, 3800));
                    Players.closest("qmanqmanaa").interact("Trade with");
                    if (Widgets.getWidgetChild(162, 56, 0).getText().contains("Sending trade offer...") && Widgets.getWidgetChild(162, 56, 0) != null) {
                        sleepUntil(() -> Trade.isOpen(1), 120000);
                    }
                    if (Widgets.getWidgetChild(162, 56, 0).getText().contains("Other player is busy at the moment.") && Widgets.getWidgetChild(162, 56, 0) != null) {
                        sleep(randomNum(15000, 25000));
                    }
                }
                if (Trade.isOpen(1)) {
                    if (Inventory.contains("Logs")) {
                        sleep(randomNum(750, 1300));
                        Trade.addItem("Logs", Inventory.count("Logs"));
                        sleepUntil(() -> !Inventory.contains("Logs"), 5000);
                    }
                }
                if (!Inventory.contains("Logs") && Trade.acceptTrade(2)) {
                    sleep(randomNum(1000, 2000));
                    Trade.acceptTrade(2);
                    sleepUntil(() -> !Trade.isOpen(), 5000);
                    log("Trade completed!");
                    this.traded = true;
                }
            }
        }

        return 0;
    }


    public void onStart() {
        log("Bot started. Logging in.");
        sleep(randomNum(1000, 2000));

        //initiating socket client (change address and ip if you need to)
        serverConnection = new ServerConnection("127.0.0.1", 1234);
        serverConnection.connect();
    }


    public void onExit() {
        log("Bot Ended");
    }


    //Random number generator for sleeping (mimics human "brain afk").
    public int randomNum(int i, int k) {
        int number = (int) (Math.random() * (k - i + 1)) + i;
        return number;
    }


    private class ServerConnection{
        private String address;
        private int port;
        private Socket socket;
        private PrintWriter writer = null;
        private BufferedReader reader = null;
        private boolean isRunning = true;
        private int id;

        public ServerConnection(String address, int port) {
            this.address = address;
            this.port = port;
        }

        public boolean connect(){
            try {
                log("Connecting...");
                socket = new Socket(address, port);
                log("Connected to server");

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                this.writer = new PrintWriter(output, true);
                reader = new BufferedReader(new InputStreamReader(input));

                send("hello");
                String message = waitForMessage();
                assert message != null;
                id = Integer.parseInt(message);
                log("I'm client " + id);
                return true;
            } catch (ConnectException e){
                log("Connection refused");
            } catch (IOException e) {
                e.printStackTrace();
                log("IOException");
            }
            return false;
        }

        public String waitForMessage(){
            try {
                log("Waiting for message");
                while(isRunning){
                    String line = reader.readLine();
                    if(line == null){
                        log("DISCONNECTED");
                        this.kill();
                        return null;
                    } else {
                        log("Got the message: " + line);
                        return line;
                    }
                }
            } catch (SocketException e) {
                log("CONNECTION INTERRUPTED");
                this.kill();
                close();
            } catch (IOException e) {
                log("IOException");
                log(Arrays.toString(e.getStackTrace()));
                this.kill();
                close();
            }
            return null;
        }

        public void close(){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        public void kill(){
            isRunning = false;
        }

        public void send(String message){
            writer.println(message);
        }
        public void queueUp(int wordId){
            send(Integer.toString(wordId));
            log("Sent trade message to server");
            waitForMessage();
        }

    }
}
