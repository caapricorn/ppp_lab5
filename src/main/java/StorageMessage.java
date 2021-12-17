public class StorageMessage {

    private String url;
    private Integer time;

    public StorageMessage(String url, Integer time) {
        this.url = url;
        this.time = time;
    }

    public String getUrl() {
        return  this.url;
    }

    public Integer getTime() {
        return this.time;
    }

}
