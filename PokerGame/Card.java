public final class Card {

    public enum Rank {
        TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"),
        SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"),
        JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");

        private final int value;
        private final String label;

        Rank(int value, String label) {
            this.value = value;
            this.label = label;
        }

        public int value() {
            return value;
        }

        public String label() {
            return label;
        }
    }

    public enum Suit {
        CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥"), SPADES("♠");

        private final String symbol;

        Suit(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean isRed() {
            return this == HEARTS || this == DIAMONDS;
        }
    }

    private final Rank rank;
    private final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Rank rank() {
        return rank;
    }

    public Suit suit() {
        return suit;
    }

    public String display() {
        return rank.label() + suit.symbol();
    }

    @Override
    public String toString() {
        return display();
    }
}
