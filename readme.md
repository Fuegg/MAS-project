# Multi Agents Project - Chanet Lucas (Erasmus student)

This project uses JADE agents to simulate a sneaker market with sellers, buyers, a reputation service, an authenticator, and a payment service.

The `AuthenticatorAgent` checks if a sneaker is authentic with a probability-based verification.
The `PaymentAgent` gives each buyer a balance and decreases it after every payment.
The `ReputationAgent` stores seller ratings, and each buyer chooses sellers based on reputation before buying.
The `SellerAgent` adjusts market prices based on supply and demand.
The `BuyerAgent` compares offers, checks reputation, then buys the best option.

## Compile

From the repository root, compile all agents with:

```bash
javac -cp "lib/jade.jar" src/project/*.java
```

## Run the code

Test with 3 buyers : 

```bash
java -cp "lib/jade.jar;src" jade.Boot -gui "seller1:project.SellerAgent;seller2:project.SellerAgent;auth1:project.AuthenticatorAgent;rep1:project.ReputationAgent;pay1:project.PaymentAgent;buyer1:project.BuyerAgent(Jordan 1 Chicago);buyer2:project.BuyerAgent(Yeezy 350 Zebra);buyer3:project.BuyerAgent(Nike Dunk Panda)"
```
Test with 1 buyer :

```bash
java -cp "lib/jade.jar;src" jade.Boot -gui "seller1:project.SellerAgent;auth1:project.AuthenticatorAgent;rep1:project.ReputationAgent;pay1:project.PaymentAgent;buyer1:project.BuyerAgent(Jordan 1 Chicago)"
```

To test the trust flow with a seller rated `0/5` and a seller rated `5/5`, launch JADE with:

```bash
java -cp "lib/jade.jar;src" jade.Boot -gui "seller1:project.SellerAgent;seller3:project.SellerAgent;auth1:project.AuthenticatorAgent;rep1:project.ReputationAgent;pay1:project.PaymentAgent;buyer1:project.BuyerAgent(Jordan 1 Chicago);buyer2:project.BuyerAgent(Jordan 1 Chicago)"
```

## Expected flow

- `seller3` should be rejected first because its reputation starts at `0/5`.
- The buyers should then continue to `seller1`, whose reputation starts at `5/5`.
- After the legitimacy check passes, payment is processed and the transaction is completed.

## Notes

- This command is written for Windows, so it uses `;` in the classpath.
- Tested on Windows 11.
- Sellers are assumed to have unlimited stock.
- AI-assisted (BuyerAgent): trade-round correlation (`trade-*` IDs) and proposal reset between rounds (I encountered difficulties making this part functional).
- Github link : https://github.com/Fuegg/MAS-project.git