package globus.javaDemo.utils;

// Item in QuickAction
public class ActionItem {

    private String name;
    private int actionId;

    public ActionItem(int actionId, String name)
    {
        this.actionId = actionId;
        this.name = name;
    }

    public String getName() { return name; }

    public int getActionId() { return actionId; }
}
