import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Deck {
    private final List<Card> cards = new ArrayList<>(52);

    public Deck() {
        reset();
    }

    public void reset() {
        cards.clear();
        for (Card.Suit s : Card.Suit.values()) {
            for (Card.Rank r : Card.Rank.values()) {
                cards.add(new Card(r, s));
            }
        }
        Collections.shuffle(cards);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            reset();
        }
        return cards.remove(cards.size() - 1);
    }
}
