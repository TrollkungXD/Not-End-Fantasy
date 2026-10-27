import java.util.*;

public class Node {
    public enum Type { START, MONSTER, EVENT, SHOP, REST, BOSS }

    private final Type type;
    private final int layer;
    private double x, y;
    private final List<Node> next = new ArrayList<>();
    private boolean cleared;
    private Event event;
    private Map<Item, Integer> stock;

    public Node(Type type, int layer) { this.type = type; this.layer = layer; }
    public Type getType() { return type; }
    public int getLayer() { return layer; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPos(double x, double y) { this.x = x; this.y = y; }
    public List<Node> getNext() { return next; }
    public void addNext(Node n) { if (!next.contains(n)) next.add(n); }
    public boolean isCleared() { return cleared; }
    public void setCleared(boolean c) { cleared = c; }
    public Event getEvent() { return event; }
    public void setEvent(Event e) { event = e; }
    public Map<Item, Integer> getStock() { return stock; }
    public void setStock(Map<Item, Integer> s) { stock = s; }
}
