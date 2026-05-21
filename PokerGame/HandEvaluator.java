import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HandEvaluator {

    public enum HandRank {
        HIGH_CARD(1, "High Card"),
        ONE_PAIR(2, "One Pair"),
        TWO_PAIR(3, "Two Pair"),
        THREE_OF_A_KIND(4, "Three of a Kind"),
        STRAIGHT(5, "Straight"),
        FLUSH(6, "Flush"),
        FULL_HOUSE(7, "Full House"),
        FOUR_OF_A_KIND(8, "Four of a Kind"),
        STRAIGHT_FLUSH(9, "Straight Flush");

        private final int strength;
        private final String label;

        HandRank(int strength, String label) {
            this.strength = strength;
            this.label = label;
        }

        public int strength() {
            return strength;
        }

        public String label() {
            return label;
        }
    }

    public static final class HandValue implements Comparable<HandValue> {
        private final HandRank rank;
        private final List<Integer> tiebreak;

        public HandValue(HandRank rank, List<Integer> tiebreak) {
            this.rank = rank;
            this.tiebreak = List.copyOf(tiebreak);
        }

        public String label() {
            return rank.label();
        }

        @Override
        public int compareTo(HandValue o) {
            int c = Integer.compare(rank.strength(), o.rank.strength());
            if (c != 0) {
                return c;
            }
            int n = Math.min(tiebreak.size(), o.tiebreak.size());
            for (int i = 0; i < n; i++) {
                int tc = Integer.compare(tiebreak.get(i), o.tiebreak.get(i));
                if (tc != 0) {
                    return tc;
                }
            }
            return Integer.compare(tiebreak.size(), o.tiebreak.size());
        }
    }

    private HandEvaluator() {}

    public static HandValue evaluate(List<Card> hand) {
        if (hand == null || hand.size() != 5) {
            throw new IllegalArgumentException("hand must have 5 cards");
        }

        List<Integer> ranks = new ArrayList<>(5);
        Map<Integer, Integer> countByRank = new HashMap<>();
        Map<Card.Suit, Integer> countBySuit = new HashMap<>();

        for (Card c : hand) {
            int rv = c.rank().value();
            ranks.add(rv);
            countByRank.put(rv, countByRank.getOrDefault(rv, 0) + 1);
            countBySuit.put(c.suit(), countBySuit.getOrDefault(c.suit(), 0) + 1);
        }

        ranks.sort(Comparator.naturalOrder());
        boolean flush = countBySuit.values().stream().anyMatch(v -> v == 5);
        StraightInfo straight = straightInfo(ranks);

        if (straight.isStraight && flush) {
            return new HandValue(HandRank.STRAIGHT_FLUSH, List.of(straight.highCard));
        }

        List<Group> groups = groups(countByRank);
        if (groups.get(0).count == 4) {
            return new HandValue(HandRank.FOUR_OF_A_KIND, List.of(groups.get(0).rank, groups.get(1).rank));
        }
        if (groups.get(0).count == 3 && groups.get(1).count == 2) {
            return new HandValue(HandRank.FULL_HOUSE, List.of(groups.get(0).rank, groups.get(1).rank));
        }
        if (flush) {
            return new HandValue(HandRank.FLUSH, descending(ranks));
        }
        if (straight.isStraight) {
            return new HandValue(HandRank.STRAIGHT, List.of(straight.highCard));
        }
        if (groups.get(0).count == 3) {
            List<Integer> kickers = new ArrayList<>(List.of(groups.get(1).rank, groups.get(2).rank));
            kickers.sort(Comparator.reverseOrder());
            List<Integer> tb = new ArrayList<>();
            tb.add(groups.get(0).rank);
            tb.addAll(kickers);
            return new HandValue(HandRank.THREE_OF_A_KIND, tb);
        }
        if (groups.get(0).count == 2 && groups.get(1).count == 2) {
            int high = Math.max(groups.get(0).rank, groups.get(1).rank);
            int low = Math.min(groups.get(0).rank, groups.get(1).rank);
            return new HandValue(HandRank.TWO_PAIR, List.of(high, low, groups.get(2).rank));
        }
        if (groups.get(0).count == 2) {
            List<Integer> kickers = new ArrayList<>(List.of(
                    groups.get(1).rank, groups.get(2).rank, groups.get(3).rank));
            kickers.sort(Comparator.reverseOrder());
            List<Integer> tb = new ArrayList<>();
            tb.add(groups.get(0).rank);
            tb.addAll(kickers);
            return new HandValue(HandRank.ONE_PAIR, tb);
        }
        return new HandValue(HandRank.HIGH_CARD, descending(ranks));
    }

    private static List<Integer> descending(List<Integer> ascending) {
        List<Integer> out = new ArrayList<>(ascending);
        out.sort(Comparator.reverseOrder());
        return out;
    }

    private static StraightInfo straightInfo(List<Integer> sorted) {
        List<Integer> d = distinct(new ArrayList<>(sorted));
        Collections.sort(d);
        if (d.size() != 5) {
            return new StraightInfo(false, -1);
        }
        if (d.get(0) == 2 && d.get(4) == 14 && d.get(1) == 3 && d.get(2) == 4 && d.get(3) == 5) {
            return new StraightInfo(true, 5);
        }
        for (int i = 1; i < 5; i++) {
            if (d.get(i) != d.get(i - 1) + 1) {
                return new StraightInfo(false, -1);
            }
        }
        return new StraightInfo(true, d.get(4));
    }

    private static List<Integer> distinct(List<Integer> sorted) {
        List<Integer> out = new ArrayList<>();
        Integer prev = null;
        for (Integer v : sorted) {
            if (prev == null || !prev.equals(v)) {
                out.add(v);
            }
            prev = v;
        }
        return out;
    }

    private static List<Group> groups(Map<Integer, Integer> countByRank) {
        List<Group> g = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : countByRank.entrySet()) {
            g.add(new Group(e.getKey(), e.getValue()));
        }
        g.sort((a, b) -> {
            int c = Integer.compare(b.count, a.count);
            return c != 0 ? c : Integer.compare(b.rank, a.rank);
        });
        return g;
    }

    private static final class Group {
        final int rank;
        final int count;

        Group(int rank, int count) {
            this.rank = rank;
            this.count = count;
        }
    }

    private static final class StraightInfo {
        final boolean isStraight;
        final int highCard;

        StraightInfo(boolean isStraight, int highCard) {
            this.isStraight = isStraight;
            this.highCard = highCard;
        }
    }
}
