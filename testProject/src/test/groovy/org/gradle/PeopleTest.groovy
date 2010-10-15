package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class PeopleTest {
    @Test
    public void peopleExist() {
        def people = new People()
        assertNotNull(people.barry.name)
    }
}
