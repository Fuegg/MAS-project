package project;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;

public class PaymentAgent extends Agent {
    /*
     * Payment Agent to process sneaker purchases.
     */
    private HashMap<String, Integer> wallets;

    protected void setup() {
        System.out.println("Payment Agent " + getAID().getName() + " is ready.");

        /*
         * I give fixed budgets for buyers 1 and 2 (1000 is low so we will see the
         * correct behavior of buyer2 that will not be able to buy the Jordan 1
         * at like the third round of selling).
         */
        wallets = new HashMap<>();
        wallets.put("buyer1", 1500);
        wallets.put("buyer2", 1000);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-payment");
        sd.setName("JADE-secure-payment");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new PaymentProcessingServer());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Payment Agent " + getAID().getName() + " shutting down.");
    }

    private class PaymentProcessingServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String buyerName = msg.getSender().getLocalName();
                int amountToPay = Integer.parseInt(msg.getContent());
                ACLMessage reply = msg.createReply();

                // default account if buyer is not know (like buyer 3 for example)
                if (!wallets.containsKey(buyerName)) {
                    wallets.put(buyerName, 1500);
                }

                System.out.println("[Payment] Processing system checking balance for " + buyerName + " (wants to pay "
                        + amountToPay + "$)");

                if (wallets.containsKey(buyerName)) {
                    int currentBalance = wallets.get(buyerName);

                    if (currentBalance >= amountToPay) {
                        // Deduct money
                        wallets.put(buyerName, currentBalance - amountToPay);
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("PAYMENT_SUCCESS");
                        System.out.println("[Payment] SUCCESS: " + buyerName + " paid " + amountToPay
                                + "$. still has : " + wallets.get(buyerName) + "$");
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("INSUFFICIENT_FUNDS");
                        System.out.println("[Payment] REJECTED: " + buyerName
                                + " has insufficient funds. (currently has : " + currentBalance + "$)");
                    }
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("UNKNOWN_BUYER");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }
}