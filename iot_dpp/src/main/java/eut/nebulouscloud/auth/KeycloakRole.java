package eut.nebulouscloud.auth;

/**
 * Represents a Keycloak role with its attributes.
 */
public class KeycloakRole {
    public final String id;
    public final String name;
    public final String match;
    public final boolean read;
    public final boolean write;
    public final boolean send;
    public final boolean consume;
    public final boolean createAddress;
    public final boolean deleteAddress;
    public final boolean createDurableQueue;
    public final boolean deleteDurableQueue;
    public final boolean createNonDurableQueue;
    public final boolean deleteNonDurableQueue;
    public final boolean manage;
    public final boolean browse;
    public final boolean view;
    public final boolean edit;
    
    /**
     * Creates a new KeycloakRole with the specified values.
     * 
     * @param id The role ID
     * @param name The role name
     * @param match The match associated with this role
     * @param read Whether this role has read permission
     * @param write Whether this role has write permission
     * @param send Whether this role has send permission
     * @param consume Whether this role has consume permission
     * @param createAddress Whether this role has create address permission
     * @param deleteAddress Whether this role has delete address permission
     * @param createDurableQueue Whether this role has create durable queue permission
     * @param deleteDurableQueue Whether this role has delete durable queue permission
     * @param createNonDurableQueue Whether this role has create non-durable queue permission
     * @param deleteNonDurableQueue Whether this role has delete non-durable queue permission
     * @param manage Whether this role has manage permission
     * @param browse Whether this role has browse permission
     * @param view Whether this role has view permission
     * @param edit Whether this role has edit permission
     */
    public KeycloakRole(String id, String name, String match, boolean read, boolean write,
                        boolean send, boolean consume, boolean createAddress, boolean deleteAddress,
                        boolean createDurableQueue, boolean deleteDurableQueue, boolean createNonDurableQueue,
                        boolean deleteNonDurableQueue, boolean manage, boolean browse, boolean view, boolean edit) {
        this.id = id;
        this.name = name;
        this.match = match;
        this.read = read;
        this.write = write;
        this.send = send;
        this.consume = consume;
        this.createAddress = createAddress;
        this.deleteAddress = deleteAddress;
        this.createDurableQueue = createDurableQueue;
        this.deleteDurableQueue = deleteDurableQueue;
        this.createNonDurableQueue = createNonDurableQueue;
        this.deleteNonDurableQueue = deleteNonDurableQueue;
        this.manage = manage;
        this.browse = browse;
        this.view = view;
        this.edit = edit;
    }
    
    
    @Override
    public String toString() {
        return "KeycloakRole{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", match='" + match + '\'' +
                ", read=" + read +
                ", write=" + write +
                ", send=" + send +
                ", consume=" + consume +
                ", createAddress=" + createAddress +
                ", deleteAddress=" + deleteAddress +
                ", createDurableQueue=" + createDurableQueue +
                ", deleteDurableQueue=" + deleteDurableQueue +
                ", createNonDurableQueue=" + createNonDurableQueue +
                ", deleteNonDurableQueue=" + deleteNonDurableQueue +
                ", manage=" + manage +
                ", browse=" + browse +
                ", view=" + view +
                ", edit=" + edit +
                '}';
    }
} 