package univ.airconnect.statistics.repository;

import univ.airconnect.user.domain.Gender;

public interface GenderCountProjection {

    Gender getGender();

    long getCount();
}
