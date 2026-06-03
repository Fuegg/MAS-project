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

public class ReputationAgent extends Agent {

    /*
     * Reputation Agent to keep track of the sellers' reputations based on
     * buyers' feedbacks and to provide reputation scores to buyers
     * when they ask for it.
     */

    private HashMap<String, Double> reputations; // reputation scores are between 0 and 5
    private HashMap<String, Integer> transactionCount;

    protected void setup() {
        System.out.println("Reputation Agent " + getAID().getLocalName() + " is ready.");
        reputations = new HashMap<>();
        transactionCount = new HashMap<>();

        // default values for testing, seller3 is a scammer : testing the intelligence
        // of the buyers.
        reputations.put("seller1", 5.0);
        reputations.put("seller2", 4.8);
        reputations.put("seller3", 0.0);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-reputation");
        sd.setName("JADE-reputation-system");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new ReputationServer());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Reputation Agent " + getAID().getLocalName() + " shutting down.");
    }

    private class ReputationServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String content = msg.getContent();

                // A buyer is asking for the reputation of a seller
                if (msg.getPerformative() == ACLMessage.REQUEST) {

                    String sellerName = content;

                    // default value if we create another seller
                    double rep = reputations.getOrDefault(sellerName, 5.0);

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(String.valueOf(rep));
                    myAgent.send(reply);
                }

                // update the reputation based on the feedback from the buyer
                else if (msg.getPerformative() == ACLMessage.INFORM) {
                    String[] parts = content.split(":");
                    String sellerName = parts[0];
                    double change = Double.parseDouble(parts[1]);

                    double currentRep = reputations.getOrDefault(sellerName, 5.0);
                    double newRep = Math.max(0.0, Math.min(5.0, currentRep + change));

                    reputations.put(sellerName, newRep);
                    transactionCount.put(sellerName, transactionCount.getOrDefault(sellerName, 0) + 1);
                    int count = transactionCount.get(sellerName);
                    System.out.println("[Reputation] " + sellerName + " report #" + count + ". New rating : "
                            + String.format("%.1f", newRep) + "/5.0");

                }
            } else {
                block();
            }
        }
    }
}