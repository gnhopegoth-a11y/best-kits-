package com.example.kitmenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finds out that two spellings are the same kit: "Wemmbu", "wembu" and "WEMMBU" are one name,
 * "The_Director" and "THE DIRECTOR" too. Used to link the kit menu to the shop and to the kit table.
 */
final class NameMatch {
    private NameMatch() {
    }

    /** Letters and digits only, lower case. "The_Director", "THE DIRECTOR" and "thedirector" all become "thedirector". */
    static String key(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The key with repeated letters folded: "wemmbu" and "wembu" both become "wembu". */
    static String squash(String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); ++i) {
            if (i == 0 || key.charAt(i) != key.charAt(i - 1)) {
                sb.append(key.charAt(i));
            }
        }
        return sb.toString();
    }

    /** Number of single-letter edits between two keys. */
    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); ++j) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); ++i) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); ++j) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /**
     * How many typos two keys may differ by and still count as one name.
     * Short names must match exactly (a wrong guess is worse than no link), longer ones may differ by 1 or 2 letters.
     */
    static int allowed(String a, String b) {
        int n = Math.min(a.length(), b.length());
        if (n <= 4) {
            return 0;
        }
        return n <= 8 ? 1 : 2;
    }

    /** The smallest typo distance between any spelling on the left and any on the right, or -1 if too far apart. */
    private static int closeness(List<String> left, List<String> right) {
        int best = -1;
        for (String l : left) {
            for (String r : right) {
                if (l.isEmpty() || r.isEmpty() || l.charAt(0) != r.charAt(0)) {
                    continue;
                }
                int d = distance(l, r);
                if (d <= allowed(l, r) && (best < 0 || d < best)) {
                    best = d;
                }
            }
        }
        return best;
    }

    private static List<String> keys(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            String k = key(n);
            if (!k.isEmpty()) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * Pairs every name on the left with at most one entry on the right (each right entry has one or more spellings).
     * Returns, for each left name, the index of its partner or -1.
     * Three rounds, strictest first: same letters, same letters with repeats folded, then a typo or two.
     * A typo match only counts when both sides pick each other and there is no tie.
     */
    static int[] pair(List<String> left, List<List<String>> right) {
        int[] match = new int[left.size()];
        Arrays.fill(match, -1);
        boolean[] taken = new boolean[right.size()];
        List<List<String>> lk = new ArrayList<>();
        for (String l : left) {
            lk.add(keys(List.of(l)));
        }
        List<List<String>> rk = new ArrayList<>();
        for (List<String> r : right) {
            rk.add(keys(r));
        }
        for (int round = 0; round < 2; ++round) {
            for (int i = 0; i < left.size(); ++i) {
                if (match[i] >= 0 || lk.get(i).isEmpty()) {
                    continue;
                }
                String a = round == 0 ? lk.get(i).get(0) : squash(lk.get(i).get(0));
                for (int j = 0; j < right.size(); ++j) {
                    if (taken[j]) {
                        continue;
                    }
                    boolean same = false;
                    for (String r : rk.get(j)) {
                        if ((round == 0 ? r : squash(r)).equals(a)) {
                            same = true;
                            break;
                        }
                    }
                    if (same) {
                        match[i] = j;
                        taken[j] = true;
                        break;
                    }
                }
            }
        }
        int[] bestRight = new int[left.size()];
        int[] bestLeft = new int[right.size()];
        Arrays.fill(bestRight, -1);
        Arrays.fill(bestLeft, -1);
        for (int i = 0; i < left.size(); ++i) {
            if (match[i] < 0) {
                bestRight[i] = bestOf(lk.get(i), rk, taken);
            }
        }
        for (int j = 0; j < right.size(); ++j) {
            if (!taken[j]) {
                bestLeft[j] = bestOfLeft(rk.get(j), lk, match);
            }
        }
        for (int i = 0; i < left.size(); ++i) {
            int j = bestRight[i];
            if (match[i] < 0 && j >= 0 && !taken[j] && bestLeft[j] == i) {
                match[i] = j;
                taken[j] = true;
            }
        }
        return match;
    }

    /** Index of the one closest free right entry, -1 if none is close enough or two are equally close. */
    private static int bestOf(List<String> l, List<List<String>> rk, boolean[] taken) {
        int best = -1;
        int bestD = Integer.MAX_VALUE;
        boolean tie = false;
        for (int j = 0; j < rk.size(); ++j) {
            if (taken[j]) {
                continue;
            }
            int d = closeness(l, rk.get(j));
            if (d < 0) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = j;
                tie = false;
            } else if (d == bestD) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    /** Index of the one closest unmatched left entry, -1 if none is close enough or two are equally close. */
    private static int bestOfLeft(List<String> r, List<List<String>> lk, int[] match) {
        int best = -1;
        int bestD = Integer.MAX_VALUE;
        boolean tie = false;
        for (int i = 0; i < lk.size(); ++i) {
            if (match[i] >= 0) {
                continue;
            }
            int d = closeness(lk.get(i), r);
            if (d < 0) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = i;
                tie = false;
            } else if (d == bestD) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    /** Convenience for one spelling per entry on the right. */
    static int[] pairSimple(List<String> left, List<String> right) {
        List<List<String>> wrapped = new ArrayList<>();
        for (String r : right) {
            wrapped.add(List.of(r));
        }
        return pair(left, wrapped);
    }
}
