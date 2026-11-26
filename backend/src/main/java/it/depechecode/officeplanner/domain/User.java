package it.depechecode.officeplanner.domain;

import java.util.List;

/**
 * Profilo utente. minOfficeDays = 0 significa full remote.
 * availability contiene solo i giorni "speciali": un giorno assente vale FREE.
 */
public record User(
        String id,
        String firstName,
        String lastName,
        String email,
        Seniority seniority,
        List<Skill> skills,
        String team,
        int minOfficeDays,
        List<DayStatus> availability
) {

    public User {
        skills = skills == null ? List.of() : List.copyOf(skills);
        availability = availability == null ? List.of() : List.copyOf(availability);
    }

    public DayStatus statusOn(String date) {
        return availability.stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElse(new DayStatus(date, OfficeNeed.FREE, null));
    }

    public OfficeNeed needOn(String date) {
        return statusOn(date).officeNeed();
    }

    public boolean hasSkill(Skill skill) {
        return skills.contains(skill);
    }

    public boolean isJunior() {
        return seniority == Seniority.JUNIOR;
    }

    public boolean isSenior() {
        return seniority == Seniority.SENIOR;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
