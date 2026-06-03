package project;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuyerAgent extends Agent {

    /*
     * Buyer agent to buy sneakers, check the sellers' reputations and the
     * legitimacy of the sneakers before buying.
     * The buyer will boycott sellers with low reputations with a certain
     * probability, and will report bad sellers to the ReputationAgent.
     */

    private String targetSneaker;
    private AID[] sellerAgents = new AID[0];
    private boolean isRuined = false;
    private final List<SellerOffer> rankedOffers = new ArrayList<>();
    private final Set<String> rejectedSellers = new HashSet<>();
    private int nextOfferIndex = 0;
    private int repliesCnt = 0;
    private int expectedReplies = 0;
    private boolean tradeInProgress = false;
    private long tradeSequence = 0;
    private String currentTradeId = null;

    private static class SellerOffer {
        private final AID seller;
        private final int price;

        private SellerOffer(AID seller, int price) {
            this.seller = seller;
            this.price = price;
        }
    }

    protected void setup() {
        Object[] args = getArguments(); // sneaker name passed as argument

        if (args != null && args.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                sb.append(args[i].toString());
                if (i < args.length - 1) {
                    sb.append(" ");
                }
            }
            targetSneaker = sb.toString();
        } else {
            targetSneaker = "Jordan 1 Chicago"; // default sneaker to buy
        }

        System.out.println("Buyer Agent " + getAID().getLocalName() + " is ready to buy: " + targetSneaker);

        addBehaviour(new TickerBehaviour(this, 15000) {
            protected void onTick() {
                if (isRuined) {
                    System.out.println(myAgent.getLocalName() + " has no money left. Window shopping stopped.");
                    stop();
                    return;
                }

                if (tradeInProgress) {
                    System.out.println(
                            myAgent.getLocalName() + " is still processing " + currentTradeId + ". waiting...");
                    return;
                }

                rankedOffers.clear();
                rejectedSellers.clear();
                nextOfferIndex = 0;
                repliesCnt = 0;
                expectedReplies = 0;
                currentTradeId = "trade-" + (++tradeSequence);

                System.out
                        .println(myAgent.getLocalName() + " [" + currentTradeId + "] searching for: " + targetSneaker);

                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("sneaker-selling");
                template.addServices(sd);

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    sellerAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        sellerAgents[i] = result[i].getName();
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

                if (sellerAgents.length > 0) {
                    expectedReplies = sellerAgents.length;
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (AID seller : sellerAgents) {
                        cfp.addReceiver(seller);
                    }

                    cfp.setContent(targetSneaker);
                    cfp.setConversationId(currentTradeId);
                    tradeInProgress = true;
                    myAgent.send(cfp);
                }

                else {
                    currentTradeId = null;
                }
            }
        });

        addBehaviour(new ReceiveProposals());
        addBehaviour(new ReceiveSystemResponses());
    }

    private class ReceiveProposals extends CyclicBehaviour {

        // receive proposals from sellers, rank them by price, and start the process of
        // checking reputation and legitimacy

        public void action() {
            if (currentTradeId == null) {
                block();
                return;
            }

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                    MessageTemplate.MatchConversationId(currentTradeId));
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                int price = Integer.parseInt(msg.getContent());
                String sellerName = msg.getSender().getLocalName();
                repliesCnt++;

                System.out.println(myAgent.getLocalName() + " [" + currentTradeId + "] received offer: " + price
                        + "$ from " + sellerName);

                rankedOffers.add(new SellerOffer(msg.getSender(), price));

                // asking for all proposals before sorting and trying the best one
                if (expectedReplies > 0 && repliesCnt >= expectedReplies) {
                    rankedOffers.sort(Comparator.comparingInt(offer -> offer.price));
                    rejectedSellers.clear();
                    nextOfferIndex = 0;

                    System.out.println(getLocalName() + " [" + currentTradeId + "] sorted " + rankedOffers.size()
                            + " offers. Checking the cheapest seller first...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    tryNextSeller();

                    repliesCnt = 0;
                    expectedReplies = 0;
                }
            } else {
                block();
            }
        }

    }

    private void tryNextSeller() {
        for (int i = nextOfferIndex; i < rankedOffers.size(); i++) {
            SellerOffer offer = rankedOffers.get(i);
            String sellerName = offer.seller.getLocalName();

            if (rejectedSellers.contains(sellerName)) {
                continue;
            }

            nextOfferIndex = i + 1;
            System.out.println(getLocalName() + " [" + currentTradeId + "] trying seller " + sellerName + " with "
                    + offer.price + "$");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            requestReputationRating(offer.seller, offer.price);
            return;
        }

        tradeInProgress = false;
        currentTradeId = null;
        System.out.println(getLocalName() + " found no acceptable seller for " + targetSneaker + "."); // All sellers
                                                                                                       // have been
                                                                                                       // rejected
    }

    private void rejectSellerAndTryNext(String sellerName, String reason) {
        // mark this seller as rejected to avoid trying them again in this trade
        // sequence
        rejectedSellers.add(sellerName);
        System.out.println(getLocalName() + " rejected " + sellerName + " (" + reason + "). Trying the next seller...");
        tryNextSeller();
    }

    private void requestReputationRating(AID seller, int price) {
        AID repAgent = null;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-reputation");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0)
                repAgent = result[0].getName();
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        if (repAgent != null) {
            ACLMessage repReq = new ACLMessage(ACLMessage.REQUEST);
            repReq.addReceiver(repAgent);
            repReq.setContent(seller.getLocalName());
            repReq.setConversationId("rep-lookup:" + seller.getLocalName() + ":" + price);
            send(repReq);
        } else {
            // checking if the ReputationAgent have been created yet.
            System.out.println(getLocalName() + " could not find a ReputationAgent. blocking the purchase for "
                    + seller.getLocalName() + ".");
        }
    }

    private class ReceiveSystemResponses extends CyclicBehaviour {
        // receive responses from the ReputationAgent, the Authenticator and the
        // PaymentAgent, and act accorddingly
        public void action() {
            MessageTemplate mt = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String conversationId = msg.getConversationId();
                if (conversationId == null)
                    return;

                if (conversationId.startsWith("rep-lookup:")) {
                    String[] parts = conversationId.split(":");
                    String sellerName = parts[1];
                    String price = parts[2];

                    try {
                        String rawRating = msg.getContent().replace(",", "."); // test
                        double rating = Double.parseDouble(rawRating);

                        if (rating <= 0.0) {
                            rejectSellerAndTryNext(sellerName, "Rating: " + rating + "/5.0");
                            return;
                        }

                        // calcul of the boycottchance & risk
                        double boycottChance = (5.0 - rating) / 4.0;
                        if (boycottChance < 0)
                            boycottChance = 0;
                        if (boycottChance > 1)
                            boycottChance = 1;

                        if (Math.random() < boycottChance) {
                            rejectSellerAndTryNext(sellerName,
                                    "Too risky : boycott chance " + (int) (boycottChance * 100) + "%");
                        } else {
                            System.out.println(myAgent.getLocalName() + " trusts " + sellerName
                                    + " enough to proceed (Rating: " + rating + "/5.0 Boycott Chance: "
                                    + (int) (boycottChance * 100) + "%). Requesting Legit Check...");
                            requestLegitCheck(sellerName, price);
                        }
                    } catch (Exception e) {
                        requestLegitCheck(sellerName, price);
                    }
                }

                // authentificator response
                else if (conversationId.startsWith("check:")) {
                    String[] parts = conversationId.split(":");
                    String sellerName = parts[1];
                    String price = parts[2];

                    if (msg.getPerformative() == ACLMessage.INFORM && "PASS".equals(msg.getContent())) {
                        System.out.println(myAgent.getLocalName() + " [" + currentTradeId
                                + "]: Legit Check passed. Contacting Payment Agent...");
                        requestPayment(sellerName, price);
                    } else {
                        System.out.println(myAgent.getLocalName() + " [" + currentTradeId + "] caught fraud. Reporting "
                                + sellerName + " to Reputation Agent.");
                        sendReputationUpdate(sellerName, "-1.5");
                        rejectSellerAndTryNext(sellerName, "Fake detected by authenticator");
                    }
                }

                // bank response
                else if (conversationId.startsWith("pay:")) {
                    String[] parts = conversationId.split(":");
                    String sellerName = parts[1];
                    String price = parts[2];

                    if (msg.getPerformative() == ACLMessage.INFORM && "PAYMENT_SUCCESS".equals(msg.getContent())) {
                        System.out.println(myAgent.getLocalName() + " [" + currentTradeId
                                + "]: Payment cleared. Finalizing order and leaving 5 stars.");

                        ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        accept.addReceiver(new AID(sellerName, AID.ISLOCALNAME));
                        accept.setContent(targetSneaker + ":" + price);
                        myAgent.send(accept);

                        tradeInProgress = false;
                        currentTradeId = null;

                        sendReputationUpdate(sellerName, "+0.5");
                    } else {
                        System.out.println(myAgent.getLocalName() + " [" + currentTradeId
                                + "]: Transaction refused because of insufficient funds.");
                        if ("INSUFFICIENT_FUNDS".equals(msg.getContent())) {
                            isRuined = true;
                            tradeInProgress = false;
                            currentTradeId = null;
                        }
                    }
                }
            } else {
                block();
            }
        }
    }

    private void requestLegitCheck(String sellerName, String price) {
        // find the AuthenticatorAgent to request a legitimacy check for the sneaker
        // before buying
        AID authenticator = null;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-authenticating");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0)
                authenticator = result[0].getName();
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        if (authenticator != null) {
            ACLMessage authReq = new ACLMessage(ACLMessage.REQUEST);
            authReq.addReceiver(authenticator);
            authReq.setContent(targetSneaker);
            authReq.setConversationId("check:" + sellerName + ":" + price);
            this.send(authReq);
        }
    }

    private void requestPayment(String sellerName, String price) {
        // find the PaymentAgent to request payment
        AID paymentAgent = null;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-payment");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0)
                paymentAgent = result[0].getName();
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        if (paymentAgent != null) {
            ACLMessage payReq = new ACLMessage(ACLMessage.REQUEST);
            payReq.addReceiver(paymentAgent);
            payReq.setContent(price);
            payReq.setConversationId("pay:" + sellerName + ":" + price);
            this.send(payReq);
        }
    }

    private void sendReputationUpdate(String sellerName, String scoreChange) {
        // find the ReputationAgent to report a reputation change for a seller
        AID repAgent = null;
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sneaker-reputation");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0)
                repAgent = result[0].getName();
        } catch (FIPAException fe) {
        }

        if (repAgent != null) {
            ACLMessage repMsg = new ACLMessage(ACLMessage.INFORM);
            repMsg.addReceiver(repAgent);
            repMsg.setContent(sellerName + ":" + scoreChange);
            this.send(repMsg);
        }
    }
}