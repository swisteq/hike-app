package pl.gorskie.wyprawy.service;

public class TrailNotFoundException extends RuntimeException {
    public TrailNotFoundException(Long id) {
        super("Trasa o id=" + id + " nie istnieje");
    }
}
