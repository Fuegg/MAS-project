package project;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.Random;

public class AuthenticatorAgent extends Agent {
    /*
     * Authenticator Agent to check if the sneakers are fake or not with a given
     * probability.
     */

    private Random random = new Random();

    protected void setup() {
        System.out.println("Authenticator Agent " + getAID().getName() + " is ready.");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-authenticating");
        sd.setName("JADE-legit-check");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new LegitCheckServer());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Authenticator Agent " + getAID().getName() + " shutting down.");
    }

    private class LegitCheckServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String sneakerName = msg.getContent();

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);

                // they are legit 80% of the time, and fake 20% of the time
                if (random.nextDouble() > 0.20) {
                    reply.setContent("PASS");
                    System.out.println("[LegitCheck] " + sneakerName + " passed authentication.");
                } else {
                    reply.setContent("FAIL");
                    System.out.println("[LegitCheck] ALERT! " + sneakerName + " are fake.");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }
}