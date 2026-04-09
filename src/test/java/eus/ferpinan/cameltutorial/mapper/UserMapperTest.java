package eus.ferpinan.cameltutorial.mapper;

import eus.ferpinan.cameltutorial.model.UserContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
    }

    @Test
    @DisplayName("Should map a valid CSV line with semicolons to UserContact")
    void shouldMapValidCsvLine() {
        String line = "Andoni;Fernandez;Gran Via;42;666777888";

        Optional<UserContact> result = userMapper.csvToUserContact(line);

        assertTrue(result.isPresent());
        UserContact user = result.get();
        assertEquals("Andoni Fernandez", user.fullName());
        assertEquals("Gran Via 42", user.fullAddress());
        assertEquals("666777888", user.phone());
    }

    @Test
    @DisplayName("Should trim values and map correctly")
    void shouldTrimValues() {
        String line = " Jon ; Doe ; Main St ; 10 ; 123456789 ";

        Optional<UserContact> result = userMapper.csvToUserContact(line);

        assertTrue(result.isPresent());
        UserContact user = result.get();
        assertEquals("Jon Doe", user.fullName());
        assertEquals("Main St 10", user.fullAddress());
        assertEquals("123456789", user.phone());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "Andoni;Fernandez;Gran Via;42",
            "A;B;C;D;E;F",
            "Andoni,Fernandez,Calle,1,2"
    })
    @DisplayName("Should return Optional.empty for malformed or incomplete lines")
    void shouldReturnEmptyForInvalidLines(String line) {
        Optional<UserContact> result = userMapper.csvToUserContact(line);

        assertTrue(result.isEmpty(), "Should be empty for line: " + line);
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNull() {
        Optional<UserContact> result = userMapper.csvToUserContact(null);

        assertTrue(result.isEmpty());
    }
}