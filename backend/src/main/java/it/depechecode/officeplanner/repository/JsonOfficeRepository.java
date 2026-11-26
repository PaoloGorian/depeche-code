package it.depechecode.officeplanner.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.depechecode.officeplanner.domain.Booking;
import it.depechecode.officeplanner.domain.DayStatus;
import it.depechecode.officeplanner.domain.OfficeNeed;
import it.depechecode.officeplanner.domain.User;
import it.depechecode.officeplanner.domain.OfficeData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Implementazione PoC: carica il mock JSON all'avvio e tiene le prenotazioni in memoria. */
@Repository
public class JsonOfficeRepository implements OfficeRepository {

    private final ObjectMapper mapper;
    private final Resource dataFile;
    private OfficeData data;

    public JsonOfficeRepository(ObjectMapper mapper,
                                @Value("${planner.data-file}") Resource dataFile) {
        this.mapper = mapper;
        this.dataFile = dataFile;
        reset();
    }

    @Override
    public synchronized OfficeData snapshot() {
        return data;
    }

    @Override
    public synchronized void addBooking(Booking booking) {
        List<Booking> bookings = new ArrayList<>(data.bookings());
        bookings.add(booking);
        data = new OfficeData(data.week(), data.users(), data.areas(), bookings);
    }

    @Override
    public synchronized void setDayStatus(String userId, DayStatus status) {
        List<User> users = data.users().stream().map(u -> {
            if (!u.id().equals(userId)) {
                return u;
            }
            List<DayStatus> availability = new ArrayList<>(u.availability().stream()
                    .filter(d -> !d.date().equals(status.date()))
                    .toList());
            if (status.officeNeed() != OfficeNeed.FREE) {
                availability.add(status);
            }
            return new User(u.id(), u.firstName(), u.lastName(), u.email(), u.seniority(), u.skills(),
                    u.team(), u.minOfficeDays(), availability);
        }).toList();
        data = new OfficeData(data.week(), users, data.areas(), data.bookings());
    }

    @Override
    public synchronized void reset() {
        try (InputStream in = dataFile.getInputStream()) {
            data = mapper.readValue(in, OfficeData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile leggere " + dataFile, e);
        }
    }
}
