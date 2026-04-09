package eus.ferpinan.cameltutorial.mapper;

import eus.ferpinan.cameltutorial.model.UserContact;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserMapper {

    public Optional<UserContact> csvToUserContact(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String[] parts = line.split(";");

        if (parts.length != 5) {
            return Optional.empty();
        }

        return Optional.of(new UserContact(
                (parts[0].trim() + " " + parts[1].trim()),
                (parts[2].trim() + " " + parts[3].trim()),
                parts[4].trim()
        ));
    }
}