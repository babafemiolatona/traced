package com.tech.traced.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import com.tech.traced.dto.SpanRequest;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpanBatch {

    @NotEmpty(message = "spans cannot be empty")
    @Valid
    @JsonProperty("spans")
    private List<SpanRequest> spans;

}