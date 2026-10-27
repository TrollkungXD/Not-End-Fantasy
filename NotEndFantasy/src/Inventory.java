import java.util.*;

public class Inventory {
    private final Map<Item, Integer> items = new LinkedHashMap<>();
    public void add(Item i, int n) { items.merge(i, n, Integer::sum); }
    public boolean remove(Item i) {
        Integer c = items.get(i);
        if (c == null) return false;
        if (c <= 1) items.remove(i); else items.put(i, c - 1);
        return true;
    }
    public int count(Item i) { return items.getOrDefault(i, 0); }
    public Map<Item, Integer> entries() { return Collections.unmodifiableMap(items); }
    public void clear() { items.clear(); }
}
