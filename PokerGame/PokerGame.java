import java.util.List;

public final class PokerGame {
    private final Deck deck = new Deck();
    private final Player player = new Player();
    private final Player dealer = new Player();
    private boolean drew = false;
    private final int ante = 25;

    public void newRound() {
        deck.reset();
        drew = false;
        player.deal(deck);
        dealer.deal(deck);
    }

    public int ante() {
        return ante;
    }

    public int draw(List<Boolean> holds) {
        if (holds == null || holds.size() != 5) {
            throw new IllegalArgumentException("holds must have 5 booleans");
        }
        if (drew) {
            return 0;
        }
        drew = true;
        int replaced = player.draw(deck, holds);
        dealer.draw(deck, dealer.autoHolds());
        return replaced;
    }

    public ShowdownResult showdown() {
        int cmp = player.value().compareTo(dealer.value());
        int payout;
        String message;
        if (cmp > 0) {
            payout = ante * 4;
            message = "You win! " + player.value().label() + " beats " + dealer.value().label();
        } else if (cmp < 0) {
            payout = -ante * 2;
            message = "Dealer wins. " + dealer.value().label() + " beats " + player.value().label();
        } else {
            payout = 0;
            message = "Push — " + player.value().label();
        }
        return new ShowdownResult(message, payout);
    }

    public List<Card> playerHand() {
        return player.hand();
    }

    public List<Card> dealerHand() {
        return dealer.hand();
    }

    public HandEvaluator.HandValue playerRank() {
        return player.value();
    }

    public HandEvaluator.HandValue dealerRank() {
        return dealer.value();
    }

    public record ShowdownResult(String message, int payout) {}
}
