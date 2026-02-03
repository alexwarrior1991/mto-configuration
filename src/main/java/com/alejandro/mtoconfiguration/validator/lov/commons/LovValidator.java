package com.alejandro.mtoconfiguration.validator.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import com.alejandro.mtoconfiguration.validator.commons.GenericValidator;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;

@Component
public abstract class LovValidator<E extends Lov, DTO extends LovDTO> extends GenericValidator<DTO> {

    @Serial
    private static final long serialVersionUID = 1L;

    protected abstract LovRepository<E> getRepository();

    @Override
    public List<String> getRequiredFields() {
        return Arrays.asList("code", "description", "enabled");
    }
}
