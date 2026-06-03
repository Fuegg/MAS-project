package project;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.HashMap;

public class SellerAgent extends Agent {

    /*
     * Seller agent to sell sneakers, keep track of the demand for each sneaker
     * and adjust the prices every 10 seconds.
     */

    private HashMap<String, Integer> catalogue;
    private HashMap<String, Integer> demandCounter;

    protected void setup() {
        catalogue = new HashMap<>();
        demandCounter = new HashMap<>();

        // default prices (seller 1)
        int pandaPrice = 120;
        int jordanPrice = 500;
        int yeezyPrice = 350;

        if (getLocalName().equals("seller2")) {
            jordanPrice = 600;
            pandaPrice = 95;
            yeezyPrice = 325;
        } else if (getLocalName().equals("seller3")) {
            jordanPrice = 200;
            yeezyPrice = 120;
        }

        // Default sneaker catalogue
        catalogue.put("Nike Dunk Panda", pandaPrice);
        catalogue.put("Jordan 1 Chicago", jordanPrice);
        catalogue.put("Yeezy 350 Zebra", yeezyPrice);

        demandCounter.put("Jordan 1 Chicago", 0);
        demandCounter.put("Yeezy 350 Zebra", 0);
        demandCounter.put("Nike Dunk Panda", 0);

        System.out.println("Seller Agent " + getAID().getName() + " is ready.");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-selling");
        sd.setName("JADE-sneaker-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new MessageReceiverServer());
        addBehaviour(new PriceAdjustmentBehaviour(this, 10000));
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Seller Agent " + getAID().getName() + " shutting down.");
    }

    private class MessageReceiverServer extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();

            // buyer asking for price
            if (msg != null) {

                if (msg.getPerformative() == ACLMessage.CFP) {
                    String targetSneaker = msg.getContent();
                    ACLMessage reply = msg.createReply();

                    if (catalogue.containsKey(targetSneaker)) {
                        demandCounter.put(targetSneaker, demandCounter.get(targetSneaker) + 1);
                        int price = catalogue.get(targetSneaker);
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(price));
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("not-available");
                    }
                    myAgent.send(reply);
                }
                // Buyer accepting the price
                else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {

                    String content = msg.getContent();
                    String sneakerName = "unknown";
                    String priceStr = content;
                    if (content != null && content.contains(":")) {
                        String[] parts = content.split(":", 2);
                        sneakerName = parts[0];
                        priceStr = parts[1];
                    }

                    System.out.println("[" + myAgent.getLocalName() + "] TRANSACTION SUCCESS: "
                            + msg.getSender().getLocalName() + " bought " + sneakerName + " for " + priceStr + "$");

                    // Send a confirmation back to the buyer
                    ACLMessage confirm = msg.createReply();
                    confirm.setPerformative(ACLMessage.INFORM);
                    confirm.setContent("SALE_CONFIRMED:" + sneakerName + ":" + priceStr);
                    myAgent.send(confirm);
                }
            } else {
                block();
            }
        }
    }

    private class PriceAdjustmentBehaviour extends TickerBehaviour {
        public PriceAdjustmentBehaviour(Agent a, long period) {
            super(a, period);
        }

        protected void onTick() {
            System.out.println("----- Market Price Adjustment (" + myAgent.getLocalName() + ") -----");
            for (String sneaker : catalogue.keySet()) {
                int demand = demandCounter.get(sneaker);
                int currentPrice = catalogue.get(sneaker);

                // if demand is 2 or more increase price by 10%
                if (demand >= 2) {
                    catalogue.put(sneaker, (int) (currentPrice * 1.10));
                    System.out.println("[" + myAgent.getLocalName() + "] " + sneaker + ": High demand. New price : "
                            + catalogue.get(sneaker) + "$");
                }
                // if demand is 0 decrease price by 5%
                else if (demand == 0) {
                    catalogue.put(sneaker, (int) (currentPrice * 0.95));
                    System.out.println("[" + myAgent.getLocalName() + "] " + sneaker + ": No demand. New price : "
                            + catalogue.get(sneaker) + "$");
                }
                demandCounter.put(sneaker, 0);
            }
        }
    }
}