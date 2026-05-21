import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One seat at the table — five cards in an ArrayList. */
public final class Player {
    private final ArrayList<Card> hand = new ArrayList<>(5);
    private HandEvaluator.HandValue value =
            new HandEvaluator.HandValue(HandEvaluator.HandRank.HIGH_CARD, List.of());

    public void deal(Deck deck) {
        hand.clear();
        for (int i = 0; i < 5; i++) {
            hand.add(deck.draw());
        }
        value = HandEvaluator.evaluate(hand);
    }

    public int draw(Deck deck, List<Boolean> holds) {
        int replaced = 0;
        for (int i = 0; i < 5; i++) {
            if (!holds.get(i)) {
                hand.set(i, deck.draw());
                replaced++;
            }
        }
        value = HandEvaluator.evaluate(hand);
        return replaced;
    }

    public List<Boolean> autoHolds() {
        int[] count = new int[15];
        for (Card c : hand) {
            count[c.rank().value()]++;
        }
        ArrayList<Boolean> holds = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            holds.add(count[hand.get(i).rank().value()] >= 2);
        }
        return holds;
    }

    public List<Card> hand() {
        return Collections.unmodifiableList(hand);
    }

    public HandEvaluator.HandValue value() {
        return value;
    }
}
