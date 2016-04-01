package com.demigodsrpg.chitchatbot.ai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Quad implements Serializable {
    String id;
    List<String> related;
    String t1, t2, t3, t4;
    boolean canStart = false;
    boolean canEnd = false;

    public Quad() {
    }

    public Quad(String t1, String t2, String t3, String t4) {
        this.id = t1 + t2 + t3 + t4;
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
        this.t4 = t4;
        related = new ArrayList<>();
    }

    public String getToken(int index) {
        switch (index) {
            case 0:
                return t1;
            case 1:
                return t2;
            case 2:
                return t3;
            case 3:
                return t4;
        }
        return null;
    }

    public void addRelated(String id) {
        if (related == null) {
            related = new ArrayList<>();
        }
        related.add(id);
    }

    public void addAllRelated(List<String> ids) {
        if (related == null) {
            related = new ArrayList<>();
        }
        related.addAll(ids);
    }
    
    public void setCanStart(boolean flag) {
        canStart = flag;
    }
    
    public void setCanEnd(boolean flag) {
        canEnd = flag;
    }    
    
    public boolean canStart() {
        return canStart;
    }
    
    public boolean canEnd() {
        return canEnd;
    }

    public boolean isValid() {
        return t1 != null && t2 != null && t3 != null && t4 != null;
    }

    public List<String> getRelated() {
        if (related == null) {
            related = new ArrayList<>();
        }
        return related;
    }

    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return t1.hashCode() +
                t2.hashCode() +
                t3.hashCode() +
                t4.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if(o instanceof Quad) {
            Quad other = (Quad) o;
            return other.t1.equals(t1) &&
                    other.t2.equals(t2) &&
                    other.t3.equals(t3) &&
                    other.t4.equals(t4);
        }
        return false;
    }

    public boolean nearMatch(Quad quad) {
        boolean m1 = quad.t1.equalsIgnoreCase(this.t1);
        boolean m2 = quad.t2.equalsIgnoreCase(this.t2);
        boolean m3 = quad.t3.equalsIgnoreCase(this.t3);
        boolean m4 = quad.t4.equalsIgnoreCase(this.t4);
        return m1 && m2 && m3 || m1 && m2 && m4 || m1 && m3 && m4 || m2 && m3 && m4;
    }
}