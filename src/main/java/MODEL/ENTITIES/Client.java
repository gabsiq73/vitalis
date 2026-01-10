package MODEL.ENTITIES;

import ENUMS.ClientType;

public class Client {
    private Integer id;
    private String name;
    private String adress;
    private String notes;
    private ClientType type;

    public Client(){
    }

    public Client(String adress, Integer id, String name, String notes, ClientType type) {
        this.adress = adress;
        this.id = id;
        this.name = name;
        this.notes = notes;
        this.type = type;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdress() {
        return adress;
    }

    public void setAdress(String adress) {
        this.adress = adress;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ClientType getType() {
        return type;
    }

    public void setType(ClientType type) {
        this.type = type;
    }
}
