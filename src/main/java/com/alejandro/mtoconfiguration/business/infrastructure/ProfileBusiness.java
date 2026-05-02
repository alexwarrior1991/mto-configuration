package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import org.springframework.stereotype.Component;

@Component
public class ProfileBusiness extends CRUDBusiness<ProfileDTO, Profile> {
}
