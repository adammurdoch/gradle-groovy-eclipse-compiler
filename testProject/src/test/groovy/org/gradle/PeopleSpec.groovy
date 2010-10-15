package org.gradle

import spock.lang.Specification

class PeopleSpec extends Specification {
    def peopleExist() {
        expect:
        def people = new People()
        people.barry.name != null
    }
}
