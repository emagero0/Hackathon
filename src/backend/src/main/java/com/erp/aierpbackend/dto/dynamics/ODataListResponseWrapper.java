package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic wrapper for OData responses that return a list of entities under the "value" key.
 * @param <T> The type of the DTO representing the entity.
 */
@Data
@NoArgsConstructor
public class ODataListResponseWrapper<T> {

    @JsonProperty("value")
    private List<T> value;

}
