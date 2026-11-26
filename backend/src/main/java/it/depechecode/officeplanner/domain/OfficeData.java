package it.depechecode.officeplanner.domain;

import java.util.List;
import java.util.Optional;

/** Fotografia immutabile dei dati su cui lavora il motore. */
public record OfficeData(List<String> week, List<User> users, List<Area> areas, List<Booking> bookings) {

    public OfficeData {
        week = week == null ? List.of() : List.copyOf(week);
        users = users == null ? List.of() : List.copyOf(users);
        areas = areas == null ? List.of() : List.copyOf(areas);
        bookings = bookings == null ? List.of() : List.copyOf(bookings);
    }

    public Optional<User> user(String id) {
        return users.stream().filter(u -> u.id().equals(id)).findFirst();
    }

    public Optional<Area> area(String id) {
        return areas.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public List<Booking> bookingsOn(String date) {
        return bookings.stream().filter(b -> b.date().equals(date)).toList();
    }

    public Optional<Booking> bookingOf(String userId, String date) {
        return bookings.stream()
                .filter(b -> b.userId().equals(userId) && b.date().equals(date))
                .findFirst();
    }

    public long occupiedSeats(String areaId, String date) {
        return bookings.stream().filter(b -> b.areaId().equals(areaId) && b.date().equals(date)).count();
    }
}
