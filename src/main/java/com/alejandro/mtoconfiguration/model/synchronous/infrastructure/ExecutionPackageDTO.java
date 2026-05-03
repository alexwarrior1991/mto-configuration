package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExecutionPackageDTO extends BaseDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private Boolean initialPackage;
    private Long length;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    private boolean enabled;
    private Long companyId;
    private List<TrackDTO> tracks = new ArrayList<>();
    private List<StationDTO> stations = new ArrayList<>();
}
