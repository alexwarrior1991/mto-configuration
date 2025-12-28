package com.alejandro.mtoconfiguration.model.commons;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@Getter
@Setter
public abstract class BaseDTO implements Comparable<BaseDTO>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    Long id;
    String createUser;
    String versionUser;
    LocalDateTime createDate;
    LocalDateTime versionDate;
    Integer versionNumber;

    @JsonIgnore
    List<Alert> alerts;
    @JsonIgnore
    String servicePath;

    public List<Alert> getAlerts() {
        if (alerts == null) {
            alerts = new ArrayList<>();
        }
        return alerts;
    }

    public BaseDTO setAlerts(List<Alert> alerts) {
        this.alerts = alerts;

        return this;
    }

    public static Long getDTOId(BaseDTO dto) {
        return Optional.ofNullable(dto).map(BaseDTO::getId).orElse(null);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return new EqualsBuilder().append(this.getClass(), o.getClass())
                .append(this.getId(), ((BaseDTO) o).getId()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).append(getId()).append(this.getClass().getName()).toHashCode();
    }

    @Override
    public int compareTo(BaseDTO o) {
        if (o == null) {
            return -1;
        }
        if (this.getId() == null) {
            return -1;
        }
        return this.getId().compareTo(o.getId());
    }
}
